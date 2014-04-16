package office.api.main.cybozu2ical;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.TimeZone;
import java.util.logging.Logger;

import javax.xml.namespace.QName;

import org.apache.axiom.om.OMAttribute;
import org.apache.axiom.om.OMElement;
import org.apache.axis2.AxisFault;
import org.apache.commons.httpclient.ConnectTimeoutException;


import jp.co.joyzo.office.api.schedule.ScheduleGetEventsByTarget;
import jp.co.joyzo.office.api.schedule.util.Span;
import jp.co.joyzo.office.api.base.BaseGetUsersByLoginName;
import jp.co.joyzo.office.api.common.CBServiceClient;

import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.model.PropertyList;
import net.fortuna.ical4j.model.Recur;
import net.fortuna.ical4j.model.ValidationException;
import net.fortuna.ical4j.model.WeekDay;
import net.fortuna.ical4j.model.DateList;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.*;

public class Cybozu2iCal {

  // ユーザーを識別する項目
  private static final String KEYITEM_ID = "id";
  private static final String KEYITEM_NAME = "name";

  private static Logger logger = Logger.getLogger("cybozu2ical");

  // 日付用ユーティリティ
  private static final String DATETIME_FORMAT = "yyyy'-'MM'-'dd'T'HH':'mm':'ss'Z'";
  private static SimpleDateFormat DATETIME_FORMATTER;
  private static final String ALLDAY_FORMAT = "yyyy'-'MM'-'dd";
  private static SimpleDateFormat ALLDAY_FORMATTER;
  private static final String LOCAL_DATETIME_FORMAT = "yyyy'-'MM'-'dd'T'HH':'mm':'ss";
  private static SimpleDateFormat LOCAL_DATETIME_FORMATTER;
  static {
    DATETIME_FORMATTER = new SimpleDateFormat(DATETIME_FORMAT);
    DATETIME_FORMATTER.setTimeZone(TimeZone.getTimeZone("UTC"));
    ALLDAY_FORMATTER = new SimpleDateFormat(ALLDAY_FORMAT);
    ALLDAY_FORMATTER.setTimeZone(TimeZone.getTimeZone("UTC"));
    LOCAL_DATETIME_FORMATTER = new SimpleDateFormat(LOCAL_DATETIME_FORMAT);
  }

  private static Date parseDate(String str) {
    Date date = null;
    try {
      date = DATETIME_FORMATTER.parse(str);
    } catch (ParseException pe) {
      try {
        date = LOCAL_DATETIME_FORMATTER.parse(str);
      } catch (ParseException pe1) {
        try {
          date = ALLDAY_FORMATTER.parse(str);
        } catch (ParseException pe2) {
          logger.warning("incorrect date format: " + pe2.getMessage());
        }
      }
    }
    return date;
  }

  private static String uidFormat = "%s@";

  private static String sysUserDir = System.getProperty("user.dir");
  private static String sysFileSeparator = System.getProperty("file.separator");

  /**
   * サイボウズOfficeからスケジュールを取得し、iCalendar形式のファイルを出力します。
   * 
   * @param args
   *          オプション
   */
  public static void main(String[] args) {
    logger.info("Begin processing");

    // プロパティファイルの読み込み
    String propertiesFile = sysUserDir + sysFileSeparator
        + "cybozu2ical.properties";
    String inputFile = "";

    // 引数チェック
    OptionsParser parser = null;
    try {
      parser = new OptionsParser(args);
    } catch (org.apache.commons.cli.ParseException e) {
      logger.severe(e.getMessage());
      return;
    }

    // プロパティファイル名の取得
    if (parser.hasPropertiesFileName())
      propertiesFile = parser.getPropertiesFileName();

    // 登録用CSVファイル名の取得
    if (parser.hasInputFileName())
      inputFile = parser.getInputFileName();
    else {
      logger.severe("Missing -i option");
      return;
    }

    // プロパティファイルの読み込み
    logger.info("Reading a property file");
    Config config = null;
    try {
      config = new Config(propertiesFile);
      if (!checkConfig(config)) {
        return;
      }
    } catch (FileNotFoundException e) {
      logger.severe(e.getMessage());
      return;
    } catch (IOException e) {
      logger.severe(e.getMessage());
      return;
    }

    URI url = config.getOfficeURL();
    uidFormat = "%s@" + url.getHost();

    logger.info("Finished reading a property file");

    String exportDir = config.getExportDir();
    if (!exportDir.substring(exportDir.length() - sysFileSeparator.length())
        .equals(sysFileSeparator))
      exportDir += sysFileSeparator;

    File dir = new File(exportDir);
    if (!dir.exists()) {
      logger.severe("Cannot find (" + exportDir + ")");
      return;
    }

    // CSVファイルの読み込み
    List<String> inputDataList = new ArrayList<String>();
    try {
      BufferedReader reader = new BufferedReader(new FileReader(inputFile));
      logger.info("Open file (" + inputFile + ")");
      String line;
      while ((line = reader.readLine()) != null) {
        inputDataList.add(line);
      }
      reader.close();
      logger.info("Close file (" + inputFile + ")");
    } catch (FileNotFoundException e) {
      logger.severe(e.getMessage());
      return;
    } catch (IOException e) {
      logger.severe(e.getMessage());
      return;
    }

    // SOAPクライアントのオブジェクト生成
    CBServiceClient client;
    try {
      client = new CBServiceClient();
      client.load(config.getOfficeURL(), config.getUsername(),
          config.getPassword());
    } catch (AxisFault e) {
      logger.severe(e.getMessage());
      return;
    }

    int succeeded = 0;
    for (String data : inputDataList) {
      logger.info("Begin Processing: " + data);

      String[] columns = data.split(",");

      if (columns.length < 3) {
        logger.warning("Insufficient number of items: " + data);
        continue;
      }

      String loginName = columns[0];
      String startDate = columns[1];
      String endDate = columns[2];

      // generate loginID for getEventsByTarget
      String loginID;
      if (config.getKeyItem().trim().equals(KEYITEM_NAME)) {
        loginID = getLoginIDByLoginName(client, columns[0]);
      } else {
        loginID = columns[0];
      }
      if (loginID == null) {
        continue;
      }

      // generate span for getEventsByTarget
      Span span = new Span();
      Date spanStart = parseDate(startDate);
      if (spanStart != null) {
        span.setStart(spanStart);
      }
      Date spanEnd = parseDate(endDate);
      if (spanEnd != null) {
        span.setEnd(spanEnd);
      }

      OMElement result = getEventsByTarget(client, loginID, span);
      if (parser.isDebug()) {
        System.out.println(result.toString());
      }
      List<VEvent> eventList = createEventList(result);

      net.fortuna.ical4j.model.Calendar calendar = new net.fortuna.ical4j.model.Calendar();
      calendar.getProperties().add(new ProdId("cybozu2ical-java/0.01"));
      calendar.getProperties().add(CalScale.GREGORIAN);
      calendar.getProperties().add(Version.VERSION_2_0);
      Iterator<VEvent> eventIter = eventList.iterator();
      while (eventIter.hasNext()) {
        calendar.getComponents().add(eventIter.next());
      }

      try {
        String exportFile = exportDir + loginName + ".ics";
        FileOutputStream out = new FileOutputStream(exportFile);
        CalendarOutputter outputter = new CalendarOutputter();
        outputter.output(calendar, out);
        logger.info("Created (" + exportFile + ")");
      } catch (IOException e) {
        logger.severe(e.getMessage());
        return;
      } catch (ValidationException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }

      logger.info("End Processing: " + data);
      succeeded++;
    }

    int total = inputDataList.size();
    logger.info("End Processing (total:" + total + " succeeded:" + succeeded
        + " failed:" + (total - succeeded) + ")");
  }

  /**
   * propertiesファイルの内容をチェックします。 必須漏れや形式間違いがある場合はfalseを返します。
   * 
   * @param config
   *          propertiesファイル情報
   * @return エラーの有無
   */
  private static boolean checkConfig(Config config) {
    boolean success = true;

    URI uri = config.getOfficeURL();
    String username = config.getUsername();
    String pwd = config.getPassword();
    String keyitem = config.getKeyItem();
    String exportdir = config.getExportDir();

    if (uri == null || uri.toString().trim().equals("")) {
      logger.severe(createErrMsg(Config.ConfigKeys.OFFICEURL.getKey()));
      success = false;
    }

    if (username == null || username.trim().equals("")) {
      logger.severe(createErrMsg(Config.ConfigKeys.USERNAME.getKey()));
      success = false;
    }

    if (pwd == null) {
      logger.severe(createErrMsg(Config.ConfigKeys.PASSWORD.getKey()));
      success = false;
    }

    if (keyitem == null) {
      logger.severe(createErrMsg(Config.ConfigKeys.KEYITEM.getKey()));
      success = false;
    } else { // 設定値が「id」、「name」以外の場合
      if (!keyitem.equals(KEYITEM_ID) && !keyitem.equals(KEYITEM_NAME)) {
        logger.severe(createErrMsg(Config.ConfigKeys.KEYITEM.getKey()));
        success = false;
      }
    }

    if (exportdir == null) {
      logger.severe(createErrMsg(Config.ConfigKeys.EXPORTDIR.getKey()));
      success = false;
    }

    return success;
  }

  /**
   * propertiesファイルの内容チェック時でエラー時に出力されるメッセージを生成します。
   * 
   * @param data
   *          エラーメッセージに表示させるチェック対象となるキー名
   * @return エラーメッセージ
   */
  private static String createErrMsg(String data) {
    return "Value is not set correctly for " + data + " in properties file";
  }

  /**
   * loginNameをloginIDに変換します。
   * 
   * @param client
   *          サイボウズOfficeのクライアントオブジェクト
   * @param loginName
   *          ログイン名
   * @return ログインID
   */
  private static String getLoginIDByLoginName(CBServiceClient client,
      String loginName) {

    BaseGetUsersByLoginName action = new BaseGetUsersByLoginName();
    action.addLoginName(loginName);

    String loginID = null;
    try {
      OMElement result = client.sendReceive(action);
      if (result != null) {
        OMElement user = result.getFirstElement().getFirstElement();
        loginID = user.getAttributeValue(new QName("key"));
      }
    } catch (ConnectTimeoutException e) {
      logger.severe(e.getMessage());
    } catch (AxisFault ae) {
      OMElement errCode = ae.getDetail();
      if (errCode != null) {
        if (errCode.getText().equals("19106")) {
          logger.severe("19106: Wrong login name (" + loginName + ")");
        } else if (errCode.getText().equals("19108")) {
          logger.severe("19108: Wrong login name or password");
        } else {
          logger.severe(errCode + ": " + loginName);
        }
      } else {
        logger.severe(loginName + ": " + ae.getMessage());
      }
    } catch (Throwable t) {
      logger.severe(t.getMessage());
    }

    return loginID;
  }

  /**
   * ユーザのスケジュールデータを取得します。
   * 
   * @param client
   *          サイボウズOfficeのクライアントオブジェクト
   * @param loginID
   *          ログインID
   * @param span
   *          日時の範囲
   * @return ユーザのスケジュールデータ
   */
  private static OMElement getEventsByTarget(CBServiceClient client,
      String loginID, Span span) {

    ScheduleGetEventsByTarget action = new ScheduleGetEventsByTarget();
    action.setSpan(span);
    action.addUserID(loginID);

    OMElement result = null;
    try {
      result = client.sendReceive(action);
    } catch (ConnectTimeoutException e) {
      logger.severe(e.getMessage());
    } catch (AxisFault ae) {
      OMElement errCode = ae.getDetail();
      if (errCode != null) {
        if (errCode.getText().equals("19108")) {
          logger.severe("19108: Wrong login name or password");
        } else {
          logger.severe(errCode + ": " + loginID);
        }
      } else {
        logger.severe(loginID + ": " + ae.getMessage());
      }
    } catch (Throwable t) {
      logger.severe(t.getMessage());
    }

    return result;
  }

  static WeekDay[] weekDays = { WeekDay.MO, WeekDay.TU, WeekDay.WE, WeekDay.TH,
      WeekDay.FR, WeekDay.SA, WeekDay.SU };

  private static WeekDay index2weekDay(String week) {
    int index = Integer.parseInt(week) - 1;
    return weekDays[index];
  }

  /**
   * SOAPで取得したデータからイベントデータのリストを作成します
   * 
   * @param node
   *          SOAPで取得したデータ
   * @return 作成したイベントデータのリスト
   * 
   */
  private static List<VEvent> createEventList(OMElement node) {

    HashMap<String, VEvent> eventsMap = new HashMap<String, VEvent>();

    Iterator<?> eventIter = node.getFirstElement().getChildrenWithLocalName(
        "schedule_event");
    while (eventIter.hasNext()) {
      OMElement event = (OMElement) eventIter.next();
      HashMap<String, Object> eventMap = parseScheduleEvent(event);

      PropertyList props = new PropertyList();

      if (eventMap.containsKey("id")) {
        props
            .add(new Uid(String.format(uidFormat, (String) eventMap.get("id"))));
      }
      props.add(new DtStamp());

      if (eventMap.containsKey("event_type")) {
        String eventType = (String) eventMap.get("event_type");
        if (eventType.equals("normal")) {
          if (eventMap.containsKey("allday")
              && eventMap.get("allday").equals("true")) {
            createNormalAllDayEvent(props, eventMap);
          } else {
            createNormalEvent(props, eventMap);
          }
        } else if (eventType.equals("repeat")) {
          if (eventMap.containsKey("allday")
              && eventMap.get("allday").equals("true")) {
            createRepeatedAllDayEvent(props, eventMap);
          } else {
            createRepeatedEvent(props, eventMap);
          }
        } else if (eventType.equals("banner")) {
          createBannerEvent(props, eventMap);
        }
      }

      if (eventMap.containsKey("detail")) {
        props.add(new Summary((String) eventMap.get("detail")));
      }
      if (eventMap.containsKey("description")) {
        props.add(new Description((String) eventMap.get("description")));
      }
      if (eventMap.containsKey("location")) {
        props.add(new Location((String) eventMap.get("location")));
      }

      VEvent vevent = new VEvent(props);

      String key = String.format(uidFormat, (String) eventMap.get("id"));
      if (eventsMap.containsKey(key)) {
        VEvent value = eventsMap.get(key);
        String prevDate = value.getStartDate().getValue();
        String currentDate = vevent.getStartDate().getValue();
        if (currentDate.compareTo(prevDate) < 0) {
          eventsMap.put(key, vevent);
        }
      } else {
        eventsMap.put(key, vevent);
      }
    }

    List<VEvent> eventList = new ArrayList<VEvent>();
    for (VEvent vevent : eventsMap.values()) {
      eventList.add(vevent);
    }
    return eventList;
  }

  private static void createRepeatedAllDayEvent(PropertyList props,
      HashMap<String, Object> eventMap) {
    Date dtstart = (Date) eventMap.get("condition.start");
    if (dtstart == null) {
      dtstart = (Date) eventMap.get("start");
    }
    if (dtstart != null) {
      props.add(new DtStart(new net.fortuna.ical4j.model.Date(dtstart)));
    }
    createRepeatedEvent(props, eventMap);
  }

  private static void createRepeatedEvent(PropertyList props,
      HashMap<String, Object> eventMap) {

    if (props.getProperty(net.fortuna.ical4j.model.Property.DTSTART) == null) {
      Date dtstart = (Date) eventMap.get("condition.start");
      if (dtstart == null) {
        dtstart = (Date) eventMap.get("start");
      }
      if (dtstart != null) {
        props.add(new DtStart(new net.fortuna.ical4j.model.DateTime(dtstart)));
      }
      Date dtend = (Date) eventMap.get("condition.end");
      if (dtend == null) {
        dtend = (Date) eventMap.get("end");
      }
      if (dtend != null) {
        props.add(new DtEnd(new net.fortuna.ical4j.model.DateTime(dtend)));
      }
    }

    Recur recur = null;
    String conditionType = (String) eventMap.get("condition.type");
    if (conditionType.equals("week")) {
      String conditionWeek = (String) eventMap.get("condition.week");
      recur = new Recur();
      recur.setFrequency(Recur.WEEKLY);
      recur.setWeekStartDay(index2weekDay(conditionWeek).toString());
      if (eventMap.containsKey("condition.end_date")) {
        String endDate = (String) eventMap.get("condition.end_date");
        if (eventMap.containsKey("condition.end_time")) {
          endDate += "T" + eventMap.get("condition.end_time");
        }
        recur.setUntil(new net.fortuna.ical4j.model.Date(parseDate(endDate)));
      }
    } else if (conditionType.equals("1stweek")
        || conditionType.equals("2ndweek") || conditionType.equals("3rdweek")
        || conditionType.equals("4thweek") || conditionType.equals("5thweek")) {
      int numOfWeek = Integer.parseInt(conditionType.substring(0, 1));
      String conditionWeek = (String) eventMap.get("condition.week");
      recur = new Recur();
      recur.setFrequency(Recur.MONTHLY);
      recur.getDayList().add(
          new WeekDay(index2weekDay(conditionWeek), numOfWeek));
      if (eventMap.containsKey("condition.end_date")) {
        String endDate = (String) eventMap.get("condition.end_date");
        if (eventMap.containsKey("condition.end_time")) {
          endDate += "T" + eventMap.get("condition.end_time");
        }
        recur.setUntil(new net.fortuna.ical4j.model.Date(parseDate(endDate)));
      }
    } else if (conditionType.equals("month")) {
      String conditionDay = (String) eventMap.get("condition.day");
      recur = new Recur();
      recur.setFrequency(Recur.MONTHLY);
      recur.getMonthDayList().add(Integer.parseInt(conditionDay));
      if (eventMap.containsKey("condition.end_date")) {
        String endDate = (String) eventMap.get("condition.end_date");
        if (eventMap.containsKey("condition.end_time")) {
          endDate += "T" + eventMap.get("condition.end_time");
        }
        recur.setUntil(new net.fortuna.ical4j.model.Date(parseDate(endDate)));
      }
    }
    if (recur != null) {
      props.add(new RRule(recur));
    }

    if (eventMap.containsKey("exclusiveDates.start")) {
      @SuppressWarnings("unchecked")
      List<Date> dates = (List<Date>) eventMap.get("exclusiveDates.start");
      DateList dateList = new DateList();
      for (Date date : dates) {
        dateList.add(new net.fortuna.ical4j.model.Date(date));
      }
      if (!dateList.isEmpty()) {
        dateList = new DateList(dateList,
            net.fortuna.ical4j.model.parameter.Value.DATE);
        props.add(new ExDate(dateList));
      }
    }
  }

  private static void createNormalEvent(PropertyList props,
      HashMap<String, Object> eventMap) {
    Date dtstart = (Date) eventMap.get("start");
    if (dtstart != null) {
      props.add(new DtStart(new net.fortuna.ical4j.model.DateTime(dtstart)));
    }
    Date dtend = (Date) eventMap.get("end");
    if (dtend != null) {
      props.add(new DtEnd(new net.fortuna.ical4j.model.DateTime(dtend)));
    }
  }

  private static void createNormalAllDayEvent(PropertyList props,
      HashMap<String, Object> eventMap) {
    Date dtstart = (Date) eventMap.get("start");
    if (dtstart != null) {
      props.add(new DtStart(new net.fortuna.ical4j.model.Date(dtstart)));
    }
  }

  private static void createBannerEvent(PropertyList props,
      HashMap<String, Object> eventMap) {
    Date dtstart = (Date) eventMap.get("start");
    if (dtstart != null) {
      props.add(new DtStart(new net.fortuna.ical4j.model.Date(dtstart)));
    }
    Date dtend = (Date) eventMap.get("end");
    if (dtend != null) {
      // dtend has to be incremented.
      Calendar cal = Calendar.getInstance();
      cal.setTime(dtend);
      cal.add(Calendar.DAY_OF_MONTH, 1);
      props.add(new DtEnd(new net.fortuna.ical4j.model.Date(cal.getTime())));
    }
  }

  /**
   * SOAPで取得したイベントデータからイベント情報の格納されたHashMapを生成します。
   * 
   * @param event
   *          SOAPで取得したイベントデータ
   * @return 生成されたHashMap
   * 
   */
  private static HashMap<String, Object> parseScheduleEvent(OMElement event) {

    // generate HashMap for a single event
    HashMap<String, Object> eventMap = new HashMap<String, Object>();

    // all attributes of schedule_event
    Iterator<?> attrIter = event.getAllAttributes();
    while (attrIter.hasNext()) {
      OMAttribute attr = (OMAttribute) attrIter.next();
      eventMap.put(attr.getLocalName(), attr.getAttributeValue());
    }

    // scedule_event/members/member/facility
    Iterator<?> membersIter = event.getChildrenWithLocalName("members");
    while (membersIter.hasNext()) {
      OMElement members = (OMElement) membersIter.next();
      Iterator<?> memberIter = members.getChildrenWithLocalName("member");
      while (memberIter.hasNext()) {
        OMElement member = (OMElement) memberIter.next();
        OMElement facility = member.getFirstElement();
        if (facility.getLocalName().equals("facility")) {
          eventMap.put("location",
              facility.getAttributeValue(new QName("name")));
          break;
        }
      }
    }

    // schedule_event/when/datetime
    Iterator<?> whenIter = event.getChildrenWithLocalName("when");
    while (whenIter.hasNext()) {
      OMElement when = (OMElement) whenIter.next();
      OMElement datetime = when.getFirstElement();
      if (datetime != null) {
        Iterator<?> datetimeAttrIter = datetime.getAllAttributes();
        while (datetimeAttrIter.hasNext()) {
          OMAttribute attr = (OMAttribute) datetimeAttrIter.next();
          String attrName = attr.getLocalName();
          String attrValue = attr.getAttributeValue();
          Date date = parseDate(attrValue);
          if (date != null) {
            eventMap.put(attrName, date);
          }
        }
      }
      break;
    }

    // schedule_event/repeat_info
    Iterator<?> repeatInfoIter = event.getChildrenWithLocalName("repeat_info");
    while (repeatInfoIter.hasNext()) {
      OMElement repeatInfo = (OMElement) repeatInfoIter.next();
      Iterator<?> conditionIter = repeatInfo
          .getChildrenWithLocalName("condition");

      while (conditionIter.hasNext()) {
        OMElement condition = (OMElement) conditionIter.next();
        if (condition != null) {
          Iterator<?> conditionAttr = condition.getAllAttributes();
          while (conditionAttr.hasNext()) {
            OMAttribute attr = (OMAttribute) conditionAttr.next();
            String attrName = "condition." + attr.getLocalName();
            String attrValue = attr.getAttributeValue();
            eventMap.put(attrName, attrValue);
          }
          String start_date = (String) eventMap.get("condition.start_date");
          String start_time = (String) eventMap.get("condition.start_time");
          String end_time = (String) eventMap.get("condition.end_time");
          String start = null;
          String end = null;
          if (start_date != null) {
            start = start_date;
            if (start_time != null) {
              start += "T" + start_time;
            }
            end = start_date;
            if (end_time != null) {
              end += "T" + end_time;
            }
          }
          Date startDate = parseDate(start);
          if (startDate != null) {
            eventMap.put("condition.start", startDate);
          }
          Date endDate = parseDate(end);
          if (endDate != null) {
            eventMap.put("condition.end", endDate);
          }
        }
      }
      Iterator<?> exclusiveDatetimesIter = repeatInfo
          .getChildrenWithLocalName("exclusive_datetimes");
      List<Date> exclusiveDatesStart = new ArrayList<Date>();
      List<Date> exclusiveDatesEnd = new ArrayList<Date>();
      while (exclusiveDatetimesIter.hasNext()) {
        OMElement exclusiveDatetimes = (OMElement) exclusiveDatetimesIter
            .next();
        Iterator<?> exclusiveDatetimeIter = exclusiveDatetimes
            .getChildrenWithLocalName("exclusive_datetime");
        while (exclusiveDatetimeIter.hasNext()) {
          OMElement exclusiveDatetime = (OMElement) exclusiveDatetimeIter
              .next();
          Iterator<?> exclusiveDatetimeAttr = exclusiveDatetime
              .getAllAttributes();
          while (exclusiveDatetimeAttr.hasNext()) {
            OMAttribute attr = (OMAttribute) exclusiveDatetimeAttr.next();
            String attrName = attr.getLocalName();
            String attrValue = attr.getAttributeValue();
            attrValue = attrValue.substring(0, 10);
            if (attrName.equals("start")) {
              exclusiveDatesStart.add(parseDate(attrValue));
            } else if (attrName.equals("end")) {
              exclusiveDatesEnd.add(parseDate(attrValue));
            }
          }
        }
      }
      if (exclusiveDatesStart != null) {
        eventMap.put("exclusiveDates.start", exclusiveDatesStart);
      }
      if (exclusiveDatesEnd != null) {
        eventMap.put("exclusiveDates.end", exclusiveDatesEnd);
      }
    }

    return eventMap;
  }
}
