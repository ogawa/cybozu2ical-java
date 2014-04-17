package office.api.main.cybozu2ical;

import java.rmi.RemoteException;
import java.util.Date;

import javax.xml.namespace.QName;

import jp.co.joyzo.office.api.base.BaseGetUsersByLoginName;
import jp.co.joyzo.office.api.schedule.ScheduleGetEventsByTarget;
import jp.co.joyzo.office.api.schedule.util.Span;

import org.apache.axiom.om.OMElement;
import org.apache.axis2.AxisFault;
import org.apache.commons.httpclient.ConnectTimeoutException;

public class CBClient extends jp.co.joyzo.office.api.common.CBServiceClient {

  public CBClient() throws AxisFault {
    super();
  }

  // ユーザーを識別する項目
  public static final String KEYITEM_ID = "id";
  public static final String KEYITEM_NAME = "name";

  /**
   * loginNameをloginIDに変換します。
   * 
   * @param loginName
   *          ログイン名
   * @return ログインID
   */
  public String getLoginIDByLoginName(String loginName) {
    BaseGetUsersByLoginName action = new BaseGetUsersByLoginName();
    action.addLoginName(loginName);

    String loginID = null;
    OMElement result = null;
    try {
      result = this.sendReceive(action);
    } catch (AxisFault e) {
      e.printStackTrace();
    } catch (ConnectTimeoutException e) {
      e.printStackTrace();
    } catch (RemoteException e) {
      e.printStackTrace();
    } catch (Throwable e) {
      e.printStackTrace();
    }
    if (result != null) {
      OMElement user = result.getFirstElement().getFirstElement();
      loginID = user.getAttributeValue(new QName("key"));
    }
    return loginID;
  }

  /**
   * ユーザのスケジュールデータを取得します。
   * 
   * @param loginID
   *          ログインID
   * @param spanStart
   *          開始日時
   * @param spanEnd
   *          終了日時
   * @return ユーザのスケジュールデータ
   */
  public OMElement getEventsByTarget(String loginID, Date spanStart,
      Date spanEnd) {
    Span span = new Span();
    if (spanStart != null) {
      span.setStart(spanStart);
    }
    if (spanEnd != null) {
      span.setEnd(spanEnd);
    }

    ScheduleGetEventsByTarget action = new ScheduleGetEventsByTarget();
    action.setSpan(span);
    action.addUserID(loginID);

    OMElement result = null;
    try {
      result = this.sendReceive(action);
    } catch (AxisFault e) {
      e.printStackTrace();
    } catch (ConnectTimeoutException e) {
      e.printStackTrace();
    } catch (RemoteException e) {
      e.printStackTrace();
    } catch (Throwable e) {
      e.printStackTrace();
    }
    return result;
  }

}
