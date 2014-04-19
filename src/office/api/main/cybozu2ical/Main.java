package office.api.main.cybozu2ical;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
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

    Options options = new Options();
    options.addOption("c", "config", true, "use given config file");
    options.addOption("i", "input", true, "use given input file [MANDATORY]");
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

    if (cmd.hasOption("help") || !cmd.hasOption("input")) {
      printHelp(options);
      return;
    }

    // config file
    String configFile = sysUserDir + sysFileSeparator
        + "cybozu2ical.properties";
    if (cmd.hasOption("config")) {
      configFile = cmd.getOptionValue("config");
    }
    Config config = null;
    try {
      config = new Config(configFile);
    } catch (IOException e) {
      e.printStackTrace();
      return;
    }
    if (!checkConfig(config)) {
      return;
    }

    // input file
    String inputFile = cmd.getOptionValue("input");
    List<String> inputDataList = new ArrayList<String>();
    try {
      BufferedReader reader = new BufferedReader(new FileReader(inputFile));
      String line;
      while ((line = reader.readLine()) != null) {
        inputDataList.add(line);
      }
      reader.close();
    } catch (IOException e) {
      e.printStackTrace();
      return;
    }

    // other common variables
    uidFormat = "%s@" + config.getOfficeURL().getHost();
    String exportDir = config.getExportDir();
    if (!exportDir.substring(exportDir.length() - sysFileSeparator.length())
        .equals(sysFileSeparator)) {
      exportDir += sysFileSeparator;
    }

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
      if (config.getKeyItem().trim().equals(CBClient.KEYITEM_NAME)) {
        loginID = client.getLoginIDByLoginName(columns[0]);
      } else {
        loginID = columns[0];
      }
      if (loginID == null) {
        continue;
      }

      OMElement node = client.getEventsByTarget(loginID,
          DateHelper.parseDate(startDate), DateHelper.parseDate(endDate));
      if (cmd.hasOption("debug")) {
        System.out.println(node);
      }
      CalendarGenerator generator = new CalendarGenerator(node,
          "cybozu2ical-java/0.01", uidFormat);
      net.fortuna.ical4j.model.Calendar calendar = generator.getCalendar();

      String exportFile = exportDir + loginName + ".ics";
      CalendarOutputter outputter = new CalendarOutputter();
      try {
        FileOutputStream out = new FileOutputStream(exportFile);
        outputter.output(calendar, out);
      } catch (IOException | ValidationException e) {
        e.printStackTrace();
        return;
      }
      logger.info("Created (" + exportFile + ")");
      logger.info("End Processing: " + data);
      succeeded++;
    }

    int total = inputDataList.size();
    logger.info("End Processing (total:" + total + " succeeded:" + succeeded
        + " failed:" + (total - succeeded) + ")");
  }

  private static void printHelp(Options options) {
    HelpFormatter formatter = new HelpFormatter();
    formatter.printHelp("cybozu2ical [options]", options);
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
      if (!keyitem.equals(CBClient.KEYITEM_ID)
          && !keyitem.equals(CBClient.KEYITEM_NAME)) {
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
}
