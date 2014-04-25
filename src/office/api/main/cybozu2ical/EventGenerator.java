package office.api.main.cybozu2ical;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;

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

import org.apache.axiom.om.OMElement;

public class EventGenerator {
  private HashMap<String, Object> map = null;
  private VEvent vevent = null;
  private PropertyList props = null;
  private String uidFormat = "%s@";

  public EventGenerator(OMElement node) {
    EventMap eventMap = new EventMap(node);
    this.setMap(eventMap.getMap());
    this.props = new PropertyList();
    this.generatePropsFromMap();
    this.vevent = new VEvent(props);
  }

  public EventGenerator(OMElement node, String format) {
    EventMap eventMap = new EventMap(node);
    this.setMap(eventMap.getMap());
    this.props = new PropertyList();
    this.setUidFormat(format);
    this.generatePropsFromMap();
    this.vevent = new VEvent(props);
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

  public void setMap(HashMap<String, Object> map) {
    this.map = map;
  }

  public void setProps(PropertyList props) {
    this.props = props;
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
