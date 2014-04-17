package office.api.main.cybozu2ical;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class DateHelper {

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

  static Date parseDate(String str) {
    Date date = null;
    try {
      date = DATETIME_FORMATTER.parse(str);
    } catch (ParseException e) {
      try {
        date = LOCAL_DATETIME_FORMATTER.parse(str);
      } catch (ParseException e1) {
        try {
          date = ALLDAY_FORMATTER.parse(str);
        } catch (ParseException e2) {
          e2.printStackTrace();
        }
      }
    }
    return date;
  }

}
