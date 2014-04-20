package office.api.main.cybozu2ical;

import java.io.FileOutputStream;
import java.io.IOException;
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
  public static final String PRODID = "cybozu2ical-java/0.01";
  public static final String CONFIGFILE = "cybozu2ical.properties";

  private static Logger logger = Logger.getLogger("cybozu2ical");

  /**
   * サイボウズOfficeからスケジュールを取得し、iCalendar形式のファイルを出力する。
   * 
   * @param args
   *          String[]
   */
  public static void main(String[] args) {

    // options, cmd
    Options options = getOptions();
    BasicParser parser = new BasicParser();
    CommandLine cmd = null;
    try {
      cmd = parser.parse(options, args);
    } catch (ParseException e) {
      e.printStackTrace();
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
    if (cmd.hasOption("start")) {
      spanStart = DateHelper.parseDate(cmd.getOptionValue("start"));
      if (spanStart == null) {
        printHelp(options);
        return;
      }
    }
    if (spanStart == null) {
      Calendar cal = Calendar.getInstance();
      cal.add(Calendar.MONTH, -1);
      spanStart = cal.getTime();
    }
    if (cmd.hasOption("end")) {
      spanEnd = DateHelper.parseDate(cmd.getOptionValue("end"));
      if (spanEnd == null) {
        printHelp(options);
        return;
      }
    }
    if (spanEnd == null) {
      Calendar cal = Calendar.getInstance();
      cal.add(Calendar.MONTH, 1);
      spanEnd = cal.getTime();
    }

    // Calendar file
    String calendarFile = null;
    if (cmd.hasOption("output")) {
      calendarFile = cmd.getOptionValue("output");
    } else {
      calendarFile = cmd.getOptionValue("user") + ".ics";
    }

    // config file
    Config config = null;
    try {
      config = getConfig(cmd);
    } catch (IOException e) {
      e.printStackTrace();
    }
    if (!checkConfig(config)) {
      return;
    }

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

    // loginId
    String loginID = cmd.getOptionValue("user");
    if (config.getKeyitem().equals(CBClient.KEYITEM_NAME)) {
      loginID = client.getLoginIDByLoginName(loginID);
    }
    if (loginID == null) {
      logger.severe("Failed to get login ID");
      return;
    }

    // generate calendar
    OMElement node = client.getEventsByTarget(loginID, spanStart, spanEnd);
    if (cmd.hasOption("debug")) {
      System.out.println(node);
    }
    CalendarGenerator generator = new CalendarGenerator(node, PRODID, uidFormat);
    net.fortuna.ical4j.model.Calendar calendar = generator.getCalendar();

    // output calendar
    CalendarOutputter outputter = new CalendarOutputter();
    try {
      FileOutputStream out = new FileOutputStream(calendarFile);
      outputter.output(calendar, out);
    } catch (IOException | ValidationException e) {
      e.printStackTrace();
    }
  }

  /**
   * Optionsを生成する。
   * 
   * @return options org.apache.commons.cli.Options
   */
  private static Options getOptions() {
    Options options = new Options();
    options.addOption("c", "config", true, "use given config file");
    options.addOption("o", "output", true, "use given output file");
    options.addOption("u", "user", true,
        "use given user login name or id [MANDATORY]");
    options.addOption("s", "start", true, "use given start date (YYYY-mm-dd)");
    options.addOption("e", "end", true, "use given end date (YYYY-mm-dd)");
    options.addOption("d", "debug", false, "print debugging information");
    options.addOption("h", "help", false, "print this message");
    return options;
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
   * Configを生成する。
   * 
   * @param cmd
   *          CommandLine
   * @return config Config
   */
  private static Config getConfig(CommandLine cmd) throws IOException {
    String configFile = null;
    if (cmd.hasOption("config")) {
      configFile = cmd.getOptionValue("config");
    } else {
      configFile = System.getProperty("user.dir")
          + System.getProperty("file.separator") + CONFIGFILE;
    }
    return new Config(configFile);
  }

  /**
   * propertiesファイルの内容をチェックする。
   * 
   * @param config
   *          propertiesファイル情報
   * @return エラーの有無
   */
  private static boolean checkConfig(Config config) {
    if (config.getOfficeURL() == null) {
      logger.severe("invalid or empty property: officeURL");
      return false;
    }
    if (config.getUsername() == null) {
      logger.severe("invalid or empty property: username");
      return false;
    }
    if (config.getPassword() == null) {
      logger.severe("invalid or empty property: password");
      return false;
    }
    if (config.getKeyitem() == null) {
      logger.severe("invalid or empty property: keyitem");
      return false;
    }
    return true;
  }
}
