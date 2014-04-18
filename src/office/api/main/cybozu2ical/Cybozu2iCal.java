package office.api.main.cybozu2ical;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import org.apache.axiom.om.OMElement;
import org.apache.axis2.AxisFault;
import org.apache.commons.cli.ParseException;

import net.fortuna.ical4j.data.CalendarOutputter;
import net.fortuna.ical4j.model.ValidationException;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.*;

public class Cybozu2iCal {

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
    logger.info("Begin processing");

    // プロパティファイルの読み込み
    String propertiesFile = sysUserDir + sysFileSeparator
        + "cybozu2ical.properties";
    String inputFile = "";

    // 引数チェック
    OptionsParser parser = null;
    try {
      parser = new OptionsParser(args);
    } catch (ParseException e1) {
      e1.printStackTrace();
    }

    // プロパティファイル名の取得
    if (parser.hasPropertiesFileName())
      propertiesFile = parser.getPropertiesFileName();

    // 登録用CSVファイル名の取得
    if (parser.hasInputFileName())
      inputFile = parser.getInputFileName();
    else {
      logger.severe("Missing -i option");
      return;
    }

    // プロパティファイルの読み込み
    logger.info("Reading a property file");
    Config config = null;
    try {
      config = new Config(propertiesFile);
    } catch (IOException e1) {
      e1.printStackTrace();
    }
    if (!checkConfig(config)) {
      return;
    }
    logger.info("Finished reading a property file");

    URI url = config.getOfficeURL();
    uidFormat = "%s@" + url.getHost();

    String exportDir = config.getExportDir();
    if (!exportDir.substring(exportDir.length() - sysFileSeparator.length())
        .equals(sysFileSeparator)) {
      exportDir += sysFileSeparator;
    }

    // CSVファイルの読み込み
    List<String> inputDataList = new ArrayList<String>();
    try {
      BufferedReader reader;
      reader = new BufferedReader(new FileReader(inputFile));
      logger.info("Open file (" + inputFile + ")");
      String line;
      while ((line = reader.readLine()) != null) {
        inputDataList.add(line);
      }
      reader.close();
      logger.info("Close file (" + inputFile + ")");
    } catch (IOException e1) {
      e1.printStackTrace();
      return;
    }

    // SOAPクライアントのオブジェクト生成
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
      if (parser.isDebug()) {
        System.out.println(node);
      }
      CalendarGenerator generator = new CalendarGenerator(node, "cybozu2ical-java/0.01", uidFormat);
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
