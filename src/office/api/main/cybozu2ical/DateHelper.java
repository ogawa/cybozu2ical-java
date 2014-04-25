package office.api.main.cybozu2ical;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class DateHelper {

  private static final String UTC_DATETIME_FORMAT = "yyyy'-'MM'-'dd'T'HH':'mm':'ss'Z'";
  private static SimpleDateFormat UTC_DATETIME_FORMATTER;
  private static final String LOCAL_DATETIME_FORMAT = "yyyy'-'MM'-'dd'T'HH':'mm':'ss";
  private static SimpleDateFormat LOCAL_DATETIME_FORMATTER;
  private static final String DATE_FORMAT = "yyyy'-'MM'-'dd";
  private static SimpleDateFormat DATE_FORMATTER;

  static {
    UTC_DATETIME_FORMATTER = new SimpleDateFormat(UTC_DATETIME_FORMAT);
    UTC_DATETIME_FORMATTER.setTimeZone(TimeZone.getTimeZone("UTC"));
    LOCAL_DATETIME_FORMATTER = new SimpleDateFormat(LOCAL_DATETIME_FORMAT);
    DATE_FORMATTER = new SimpleDateFormat(DATE_FORMAT);
    DATE_FORMATTER.setTimeZone(TimeZone.getTimeZone("UTC"));
  }

  static Date parseDate(String str) {
    Date date = null;
    try {
      date = UTC_DATETIME_FORMATTER.parse(str);
    } catch (ParseException e) {
      try {
        date = LOCAL_DATETIME_FORMATTER.parse(str);
      } catch (ParseException e1) {
        try {
          date = DATE_FORMATTER.parse(str);
        } catch (ParseException e2) {
          e2.printStackTrace();
        }
      }
    }
    return date;
  }

  static Date utcDaTimeFormatter(String str) {
    try {
      return UTC_DATETIME_FORMATTER.parse(str);
    } catch (ParseException e) {
      e.printStackTrace();
      return null;
    }
  }

  static Date localDateTimeFormatter(String str) {
    try {
      return LOCAL_DATETIME_FORMATTER.parse(str);
    } catch (ParseException e) {
      e.printStackTrace();
      return null;
    }
  }

  static Date DateFormatter(String str) {
    try {
      return DATE_FORMATTER.parse(str);
    } catch (ParseException e) {
      e.printStackTrace();
      return null;
    }
  }
}
