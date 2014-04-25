package office.api.main.cybozu2ical;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;

import javax.xml.namespace.QName;

import net.fortuna.ical4j.model.DateList;
import net.fortuna.ical4j.model.PropertyList;
import net.fortuna.ical4j.model.Recur;
import net.fortuna.ical4j.model.WeekDay;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.Description;
import net.fortuna.ical4j.model.property.DtEnd;
import net.fortuna.ical4j.model.property.DtStamp;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.ExDate;
import net.fortuna.ical4j.model.property.Location;
import net.fortuna.ical4j.model.property.RRule;
import net.fortuna.ical4j.model.property.Summary;
import net.fortuna.ical4j.model.property.Uid;

import org.apache.axiom.om.OMAttribute;
import org.apache.axiom.om.OMElement;

public class EventGenerator {
  private OMElement node = null;
  private HashMap<String, Object> map = null;
  private VEvent vevent = null;
  private PropertyList props = null;
  private String uidFormat = "%s@";

  public EventGenerator(OMElement node) {
    this.map = new HashMap<String, Object>();
    this.props = new PropertyList();
    this.setNode(node);
    this.generateMapFromNode();
    this.generatePropsFromMap();
    this.vevent = new VEvent(props);
  }

  public EventGenerator(OMElement node, String format) {
    this.map = new HashMap<String, Object>();
    this.props = new PropertyList();
    this.setNode(node);
    this.setUidFormat(format);
    this.generateMapFromNode();
    this.generatePropsFromMap();
    this.vevent = new VEvent(props);
  }

  public OMElement getNode() {
    return node;
  }

  public void setNode(OMElement node) {
    this.node = node;
  }

  public void setUidFormat(String format) {
    this.uidFormat = format;
  }

  public HashMap<String, Object> getMap() {
    return map;
  }

  public VEvent getVEvent() {
    return vevent;
  }

  public PropertyList getProps() {
    return props;
  }

  public void setProps(PropertyList props) {
    this.props = props;
  }

  private void generateMapFromNode() {

    // all attributes of schedule_event
    Iterator<?> attrIter = node.getAllAttributes();
    while (attrIter.hasNext()) {
      OMAttribute attr = (OMAttribute) attrIter.next();
      map.put(attr.getLocalName(), attr.getAttributeValue());
    }

    String location = this.extractLocation(node);
    if (location != null) {
      map.put("location", location);
    }

    // schedule_event/when/datetime
    Iterator<?> whenIter = node.getChildrenWithLocalName("when");
    while (whenIter.hasNext()) {
      OMElement when = (OMElement) whenIter.next();
      OMElement datetime = when.getFirstElement();
      if (datetime != null) {
        Iterator<?> datetimeAttrIter = datetime.getAllAttributes();
        while (datetimeAttrIter.hasNext()) {
          OMAttribute attr = (OMAttribute) datetimeAttrIter.next();
          String key = attr.getLocalName();
          String value = attr.getAttributeValue();
          Date date = DateHelper.parseDate(value);
          if (date != null) {
            map.put(key, date);
          }
        }
      }
      break;
    }

    // schedule_event/repeat_info
    Iterator<?> repeatInfoIter = node.getChildrenWithLocalName("repeat_info");
    while (repeatInfoIter.hasNext()) {
      OMElement repeatInfo = (OMElement) repeatInfoIter.next();

      // schedule_event/repeat_info/condition
      Iterator<?> conditionIter = repeatInfo
          .getChildrenWithLocalName("condition");
      while (conditionIter.hasNext()) {
        OMElement condition = (OMElement) conditionIter.next();
        if (condition != null) {
          Iterator<?> conditionAttr = condition.getAllAttributes();
          while (conditionAttr.hasNext()) {
            OMAttribute attr = (OMAttribute) conditionAttr.next();
            map.put("condition." + attr.getLocalName(),
                attr.getAttributeValue());
          }
        }
      }
      Date condStartDate = this.extractCondStartDate();
      if (condStartDate != null) {
        map.put("condition.start", condStartDate);
      }
      Date condEndDate = this.extractCondEndDate();
      if (condEndDate != null) {
        map.put("condition.end", condEndDate);
      }
      Date untilDate = this.extractUntilDate();
      if (untilDate != null) {
        map.put("condition.until", untilDate);
      }

      ArrayList<Date> exclusiveDates = this.extractExDates(repeatInfo);
      if (exclusiveDates != null) {
        map.put("exclusiveDates", exclusiveDates);
      }
    }
  }

  // scedule_event/members/member/facility
  private String extractLocation(OMElement node) {
    String location = null;

    Iterator<?> membersIter = node.getChildrenWithLocalName("members");
    while (membersIter.hasNext()) {
      OMElement members = (OMElement) membersIter.next();
      Iterator<?> memberIter = members.getChildrenWithLocalName("member");
      while (memberIter.hasNext()) {
        OMElement member = (OMElement) memberIter.next();
        OMElement facility = member.getFirstElement();
        if (facility.getLocalName().equals("facility")) {
          location = facility.getAttributeValue(new QName("name"));
          break;
        }
      }
    }
    return location;
  }

  private Date extractCondStartDate() {
    Date date = null;
    String start_date = (String) map.get("condition.start_date");
    String start_time = (String) map.get("condition.start_time");
    if (start_date != null) {
      String s = (start_time == null) ? start_date : start_date + "T"
          + start_time;
      date = DateHelper.parseDate(s);
    }
    return date;
  }

  private Date extractCondEndDate() {
    Date date = null;
    String start_date = (String) map.get("condition.start_date");
    String end_time = (String) map.get("condition.end_time");
    if (start_date != null) {
      String s = (end_time == null) ? start_date : start_date + "T" + end_time;
      date = DateHelper.parseDate(s);
    }
    return date;
  }

  private Date extractUntilDate() {
    Date date = null;
    String end_date = (String) map.get("condition.end_date");
    String end_time = (String) map.get("condition.end_time");
    if (end_date != null) {
      String s = (end_time == null) ? end_date : end_date + "T" + end_time;
      date = DateHelper.parseDate(s);
    }
    return date;
  }

  // schedule_event/repeat_info/exclusive_datetimes+
  private ArrayList<Date> extractExDates(OMElement node) {
    ArrayList<Date> exclusiveDates = new ArrayList<Date>();

    Iterator<?> exclusiveDatetimesIter = node
        .getChildrenWithLocalName("exclusive_datetimes");

    String offsetTime = (String) map.get("condition.start_time");
    while (exclusiveDatetimesIter.hasNext()) {
      OMElement exclusiveDatetimes = (OMElement) exclusiveDatetimesIter.next();
      Iterator<?> exclusiveDatetimeIter = exclusiveDatetimes
          .getChildrenWithLocalName("exclusive_datetime");
      while (exclusiveDatetimeIter.hasNext()) {
        OMElement exclusiveDatetime = (OMElement) exclusiveDatetimeIter.next();
        String value = exclusiveDatetime.getAttributeValue(new QName("start"));
        value = value.substring(0, 10); // YYYY-mm-dd
        if (offsetTime != null) {
          value = value + "T" + offsetTime;
        }
        Date exDate = DateHelper.parseDate(value);
        if (exDate != null) {
          exclusiveDates.add(exDate);
        }
      }
    }
    return exclusiveDates;
  }

  private void generatePropsFromMap() {
    if (map.containsKey("id")) {
      props.add(new Uid(String.format(this.uidFormat, (String) map.get("id"))));
    }
    props.add(new DtStamp());

    if (map.containsKey("event_type")) {
      String eventType = (String) map.get("event_type");
      if (eventType.equals("normal")) {
        if (map.containsKey("allday") && map.get("allday").equals("true")) {
          this.makeNormalAllDayEvent();
        } else {
          this.makeNormalEvent();
        }
      } else if (eventType.equals("repeat")) {
        if (map.containsKey("allday") && map.get("allday").equals("true")) {
          this.makeRepeatedAllDayEvent();
        } else {
          this.makeRepeatedEvent();
        }
      } else if (eventType.equals("banner")) {
        this.makeBannerEvent();
      }
    }

    if (map.containsKey("detail")) {
      props.add(new Summary((String) map.get("detail")));
    }
    if (map.containsKey("description")) {
      props.add(new Description((String) map.get("description")));
    }
    if (map.containsKey("location")) {
      props.add(new Location((String) map.get("location")));
    }
  }

  private void makeNormalEvent() {
    Date dtstart = (Date) map.get("start");
    if (dtstart != null) {
      props.add(new DtStart(new net.fortuna.ical4j.model.DateTime(dtstart)));
    }
    Date dtend = (Date) map.get("end");
    if (dtend != null) {
      props.add(new DtEnd(new net.fortuna.ical4j.model.DateTime(dtend)));
    }
  }

  private void makeNormalAllDayEvent() {
    Date dtstart = (Date) map.get("start");
    if (dtstart != null) {
      props.add(new DtStart(new net.fortuna.ical4j.model.Date(dtstart)));
    }
  }

  static WeekDay[] weekDays = { WeekDay.MO, WeekDay.TU, WeekDay.WE, WeekDay.TH,
      WeekDay.FR, WeekDay.SA, WeekDay.SU };

  private static WeekDay index2weekDay(String week) {
    int index = Integer.parseInt(week) - 1;
    return weekDays[index];
  }

  private void makeRepeatedEvent() {

    if (props.getProperty(net.fortuna.ical4j.model.Property.DTSTART) == null) {
      Date dtstart = (Date) map.get("condition.start");
      if (dtstart == null) {
        dtstart = (Date) map.get("start");
      }
      if (dtstart != null) {
        props.add(new DtStart(new net.fortuna.ical4j.model.DateTime(dtstart)));
      }
      Date dtend = (Date) map.get("condition.end");
      if (dtend == null) {
        dtend = (Date) map.get("end");
      }
      if (dtend != null) {
        props.add(new DtEnd(new net.fortuna.ical4j.model.DateTime(dtend)));
      }
    }

    Recur recur = null;
    String conditionType = (String) map.get("condition.type");
    if (conditionType.equals("week")) {
      String conditionWeek = (String) map.get("condition.week");
      recur = new Recur();
      recur.setFrequency(Recur.WEEKLY);
      recur.setWeekStartDay(index2weekDay(conditionWeek).toString());
    } else if (conditionType.equals("1stweek")
        || conditionType.equals("2ndweek") || conditionType.equals("3rdweek")
        || conditionType.equals("4thweek") || conditionType.equals("5thweek")) {
      int numOfWeek = Integer.parseInt(conditionType.substring(0, 1));
      String conditionWeek = (String) map.get("condition.week");
      recur = new Recur();
      recur.setFrequency(Recur.MONTHLY);
      recur.getDayList().add(
          new WeekDay(index2weekDay(conditionWeek), numOfWeek));
    } else if (conditionType.equals("month")) {
      String conditionDay = (String) map.get("condition.day");
      recur = new Recur();
      recur.setFrequency(Recur.MONTHLY);
      recur.getMonthDayList().add(Integer.parseInt(conditionDay));
    }
    if (recur != null) {
      if (map.containsKey("condition.until")) {
        recur.setUntil(new net.fortuna.ical4j.model.Date((Date) map
            .get("condition.until")));
      }
      props.add(new RRule(recur));
    }

    if (map.containsKey("exclusiveDates")) {
      @SuppressWarnings("unchecked")
      ArrayList<Date> dates = (ArrayList<Date>) map.get("exclusiveDates");
      if (map.containsKey("allday") && map.get("allday").equals("true")) {
        for (Date date : dates) {
          DateList dateList = new DateList();
          dateList.add(new net.fortuna.ical4j.model.Date(date));
          dateList = new DateList(dateList,
              net.fortuna.ical4j.model.parameter.Value.DATE);
          props.add(new ExDate(dateList));
        }
      } else {
        for (Date date : dates) {
          DateList dateList = new DateList();
          dateList.add(new net.fortuna.ical4j.model.DateTime(date));
          props.add(new ExDate(dateList));
        }
      }
    }
  }

  private void makeRepeatedAllDayEvent() {
    Date dtstart = (Date) map.get("condition.start");
    if (dtstart == null) {
      dtstart = (Date) map.get("start");
    }
    if (dtstart != null) {
      props.add(new DtStart(new net.fortuna.ical4j.model.Date(dtstart)));
    }
    this.makeRepeatedEvent();
  }

  private void makeBannerEvent() {
    Date dtstart = (Date) map.get("start");
    if (dtstart != null) {
      props.add(new DtStart(new net.fortuna.ical4j.model.Date(dtstart)));
    }
    Date dtend = (Date) map.get("end");
    if (dtend != null) {
      // dtend has to be incremented.
      Calendar cal = Calendar.getInstance();
      cal.setTime(dtend);
      cal.add(Calendar.DAY_OF_MONTH, 1);
      props.add(new DtEnd(new net.fortuna.ical4j.model.Date(cal.getTime())));
    }
  }
}
