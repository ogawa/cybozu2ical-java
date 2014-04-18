package office.api.main.cybozu2ical;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.CalScale;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.Version;

import org.apache.axiom.om.OMElement;

public class CalendarGenerator {
  private String uidFormat = "%s@";
  private String prodId = null;
  private OMElement node = null;
  net.fortuna.ical4j.model.Calendar calendar = null;
  
  public CalendarGenerator(OMElement node, String prodId, String format) {
    this.setNode(node);
    this.setProdId(prodId);
    this.setUidFormat(format);
    this.generateCalendar();
  }
  
  public net.fortuna.ical4j.model.Calendar getCalendar() {
    return calendar;
  }

  public void setCalendar(net.fortuna.ical4j.model.Calendar calendar) {
    this.calendar = calendar;
  }

  public String getUidFormat() {
    return uidFormat;
  }
  public void setUidFormat(String uidFormat) {
    this.uidFormat = uidFormat;
  }
  public String getProdId() {
    return prodId;
  }
  public void setProdId(String prodId) {
    this.prodId = prodId;
  }
  public OMElement getNode() {
    return node;
  }
  public void setNode(OMElement node) {
    this.node = node;
  }

  private void generateCalendar() {
    net.fortuna.ical4j.model.Calendar calendar = new net.fortuna.ical4j.model.Calendar();
    calendar.getProperties().add(new ProdId(this.getProdId()));
    calendar.getProperties().add(CalScale.GREGORIAN);
    calendar.getProperties().add(Version.VERSION_2_0);

    List<VEvent> eventList = this.getVEventList();
    Iterator<VEvent> eventIter = eventList.iterator();
    while (eventIter.hasNext()) {
      calendar.getComponents().add(eventIter.next());
    }
    this.setCalendar(calendar);
  }
  
  /**
   * SOAPで取得したデータからイベントデータのリストを作成します
   * 
   * @return 作成したイベントデータのリスト
   * 
   */
  private List<VEvent> getVEventList() {
    HashMap<String, VEvent> veventsMap = new HashMap<String, VEvent>();

    Iterator<?> eventIter = node.getFirstElement().getChildrenWithLocalName(
        "schedule_event");
    while (eventIter.hasNext()) {
      OMElement eventNode = (OMElement) eventIter.next();
      EventGenerator event = new EventGenerator(eventNode, this.getUidFormat());
      VEvent vevent = event.getVEvent();

      String uid = vevent.getUid().getValue();
      String startDate = vevent.getStartDate().getValue();
      if (veventsMap.containsKey(uid)) {
        VEvent prevVEvent = veventsMap.get(uid);
        String prevStartDate = prevVEvent.getStartDate().getValue();
        if (startDate.compareTo(prevStartDate) < 0) {
          veventsMap.put(uid, vevent);
        }
      } else {
        veventsMap.put(uid, vevent);
      }
    }

    List<VEvent> veventList = new ArrayList<VEvent>();
    for (VEvent vevent : veventsMap.values()) {
      veventList.add(vevent);
    }
    return veventList;
  }
}
