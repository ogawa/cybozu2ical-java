package office.api.main.cybozu2ical;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.Calendar;
import java.util.Date;
import java.util.logging.Logger;

import org.apache.axiom.om.OMElement;
import org.apache.axis2.AxisFault;
import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.model.ValidationException;

public class Main {

  private static Logger logger = Logger.getLogger("cybozu2ical");

  /**
   * サイボウズOfficeからスケジュールを取得し、iCalendar形式のファイルを出力する。
   * 
   * @param args
   *          String[]
   */
  public static void main(String[] args) {

    Options options = new Options();
    options.addOption("c", "config", true, "use given config file");
    options.addOption("o", "output", true, "use given output file");
    options.addOption("u", "user", true,
        "use given user login name or id [MANDATORY]");
    options.addOption("s", "start", true, "use given start date [YYYY-mm-dd]");
    options.addOption("e", "end", true, "use given end date [YYYY-mm-dd]");
    options.addOption("d", "debug", false, "print debugging information");
    options.addOption("h", "help", false, "print this message");
    BasicParser parser = new BasicParser();
    CommandLine cmd = null;

    try {
      cmd = parser.parse(options, args);
    } catch (ParseException e) {
      printHelp(options);
      return;
    }

    // help
    if (cmd.hasOption("help") || !cmd.hasOption("user")) {
      printHelp(options);
      return;
    }

    // start, end
    Date spanStart = null;
    Date spanEnd = null;
    if (cmd.hasOption("start"))
      spanStart = DateHelper.parseDate(cmd.getOptionValue("start"));
    if (spanStart == null) {
      Calendar cal = Calendar.getInstance();
      cal.add(Calendar.MONTH, -1);
      spanStart = cal.getTime();
    }
    if (cmd.hasOption("end"))
      spanEnd = DateHelper.parseDate(cmd.getOptionValue("end"));
    if (spanEnd == null) {
      Calendar cal = Calendar.getInstance();
      cal.add(Calendar.MONTH, 1);
      spanEnd = cal.getTime();
    }

    // Calendar file
    String calendarFile = null;
    if (cmd.hasOption("output"))
      calendarFile = cmd.getOptionValue("output");
    else
      calendarFile = cmd.getOptionValue("user") + ".ics";

    // config file
    String configFile = null;
    if (cmd.hasOption("config"))
      configFile = cmd.getOptionValue("config");
    else
      configFile = System.getProperty("user.dir")
          + System.getProperty("file.separator") + "cybozu2ical.properties";
    Config config = null;
    try {
      config = new Config(configFile);
    } catch (IOException e) {
      e.printStackTrace();
      return;
    }
    if (!checkConfig(config))
      return;

    // other common variables
    String uidFormat = "%s@" + config.getOfficeURL().getHost();

    // generate SOAP client
    CBClient client;
    try {
      client = new CBClient();
      client.load(config.getOfficeURL(), config.getUsername(),
          config.getPassword());
    } catch (AxisFault e) {
      e.printStackTrace();
      return;
    }

    // loginName, loginID
    String loginName = cmd.getOptionValue("user");
    String loginID = null;
    if (config.getKeyItem().trim().equals(CBClient.KEYITEM_NAME)) {
      loginID = client.getLoginIDByLoginName(loginName);
    } else {
      loginID = loginName;
    }
    if (loginID == null) {
      logger.severe("Failed to get login ID");
      return;
    }

    // generate calendar
    OMElement node = client.getEventsByTarget(loginID, spanStart, spanEnd);
    if (cmd.hasOption("debug"))
      System.out.println(node);
    CalendarGenerator generator = new CalendarGenerator(node,
        "cybozu2ical-java/0.01", uidFormat);
    net.fortuna.ical4j.model.Calendar calendar = generator.getCalendar();

    // output calendar
    CalendarOutputter outputter = new CalendarOutputter();
    try {
      FileOutputStream out = new FileOutputStream(calendarFile);
      outputter.output(calendar, out);
    } catch (IOException | ValidationException e) {
      e.printStackTrace();
      return;
    }
    logger.info("Created (" + calendarFile + ")");

  }

  /**
   * ヘルプメッセージを出力する。
   * 
   * @param options
   *          org.apache.commons.cli.Options
   */
  private static void printHelp(Options options) {
    HelpFormatter formatter = new HelpFormatter();
    formatter.printHelp("cybozu2ical -u <arg> [options]", options);
  }

  /**
   * propertiesファイルの内容をチェックする。必須漏れや形式間違いがある場合はfalseを返す。
   * 
   * @param config
   *          propertiesファイル情報
   * @return エラーの有無
   */
  private static boolean checkConfig(Config config) {
    boolean success = true;

    URI uri = config.getOfficeURL();
    String username = config.getUsername();
    String password = config.getPassword();
    String keyitem = config.getKeyItem();

    if (uri == null || uri.toString().trim().equals("")) {
      logger.severe("Invalid " + Config.ConfigKeys.OFFICEURL.getKey());
      success = false;
    }

    if (username == null || username.trim().equals("")) {
      logger.severe("Invalid " + Config.ConfigKeys.USERNAME.getKey());
      success = false;
    }

    if (password == null) {
      logger.severe("Invalid " + Config.ConfigKeys.PASSWORD.getKey());
      success = false;
    }

    if (keyitem == null) {
      logger.severe("Invalid " + Config.ConfigKeys.KEYITEM.getKey());
      success = false;
    } else { // 設定値が「id」、「name」以外の場合
      if (!keyitem.equals(CBClient.KEYITEM_ID)
          && !keyitem.equals(CBClient.KEYITEM_NAME)) {
        logger.severe("Invalid " + Config.ConfigKeys.KEYITEM.getKey());
        success = false;
      }
    }

    return success;
  }
}
