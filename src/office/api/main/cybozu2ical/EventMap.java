package office.api.main.cybozu2ical;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;

import javax.xml.namespace.QName;

import org.apache.axiom.om.OMAttribute;
import org.apache.axiom.om.OMElement;

public class EventMap {
  private HashMap<String, Object> map = null;
  private OMElement node = null;

  public EventMap(OMElement node) {
    this.node = node;
    this.map = new HashMap<String, Object>();
    this.generateMap();
  }

  public HashMap<String, Object> getMap() {
    return map;
  }

  public void setMap(HashMap<String, Object> map) {
    this.map = map;
  }

  public OMElement getNode() {
    return node;
  }

  public void setNode(OMElement node) {
    this.node = node;
  }

  private void generateMap() {

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
    String dateStr = (String) map.get("condition.start_date");
    String timeStr = (String) map.get("condition.start_time");
    if (dateStr != null) {
      String s = (timeStr == null) ? dateStr : dateStr + "T" + timeStr;
      date = DateHelper.parseDate(s);
    }
    return date;
  }

  private Date extractCondEndDate() {
    Date date = null;
    String dateStr = (String) map.get("condition.start_date");
    String timeStr = (String) map.get("condition.end_time");
    if (dateStr != null) {
      String s = (timeStr == null) ? dateStr : dateStr + "T" + timeStr;
      date = DateHelper.parseDate(s);
    }
    return date;
  }

  private Date extractUntilDate() {
    Date date = null;
    String dateStr = (String) map.get("condition.end_date");
    String timeStr = (String) map.get("condition.end_time");
    if (dateStr != null) {
      String s = (timeStr == null) ? dateStr : dateStr + "T" + timeStr;
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
}
