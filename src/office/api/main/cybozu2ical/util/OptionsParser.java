package office.api.main.cybozu2ical.util;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 * コマンドライン引数で渡されたオプションをパースします。<br />
 * このクラスは、以下のオプションのみ解析を行います。<br />
 * -c 設定ファイル（propertiesファイル）<br />
 * -i 入力ファイル名<br />
 * -s 入力ファイル文字コード<br />
 * <br />
 * このクラスはオプションの必須チェックは行いません。<br />
 * 必須チェックは各プログラムで行う必要があります。<br />
 * 
 * @author $api_author Office Team@Cybozu$
 * @version $api_version ver 1.0.0$
 */
public class OptionsParser {
  private static final String OPTION_INI_FILE = "c";
  private static final String OPTION_INPUT_FILE = "i";
  private static final String OPTION_CHARSET = "s";
  private static final String OPTION_DEBUG = "d";
  private CommandLine commandLine = null;

  /**
   * パースする対象のオプション配列を渡します。 通常は main 関数の引数が渡されます。
   * 
   * @param args
   *          オプション配列
   * @throws ParseException
   *           引数のエラー
   */
  public OptionsParser(String args[]) throws ParseException {
    // Parse command line option
    BasicParser parser = new BasicParser();
    Options options = new Options();
    options.addOption(OPTION_INI_FILE, true, "setting file");
    options.addOption(OPTION_INPUT_FILE, true, "input file");
    options.addOption(OPTION_CHARSET, true,
        "file character set(UTF-8, Shift-JIS, EUC-JP)");
    options.addOption(OPTION_DEBUG, false, "Print XML file");

    commandLine = parser.parse(options, args);
  }

  /**
   * -c オプションが存在するかどうかを返します。
   * 
   * @return -cオプションが存在する場合はtrue
   */
  public boolean hasPropertiesFileName() {
    return commandLine.hasOption(OPTION_INI_FILE);
  }

  /**
   * 設定ファイル名を返します。 -c オプションが存在しない場合は null.
   * 
   * @return java.lang.String 設定ファイル名
   */
  public String getPropertiesFileName() {
    return getValue(OPTION_INI_FILE);
  }

  /**
   * -i オプションが存在するかどうかを返します。
   * 
   * @return -iオプションが存在する場合はtrue
   */
  public boolean hasInputFileName() {
    return commandLine.hasOption(OPTION_INPUT_FILE);
  }

  /**
   * 入力ファイル名を返します。 -i オプションが存在しない場合は null.
   * 
   * @return java.lang.String 入力ファイル名
   */
  public String getInputFileName() {
    return getValue(OPTION_INPUT_FILE);
  }

  /**
   * -s オプションが存在するかどうかを返します。
   * 
   * @return -s オプションが存在する場合はtrue
   */
  public boolean hasCharactorSet() {
    return commandLine.hasOption(OPTION_CHARSET);
  }

  /**
   * 文字コード（UTF-8,Shift-JIS,EUC-JP）を返します。 -s オプションが指定されていない場合は null.
   * 
   * @return java.lang.String 出力文字コード（UTF-8,Shift-JIS,EUC-JP）
   */
  public String getCharactorSet() {
    return getValue(OPTION_CHARSET);
  }

  public boolean isDebug() {
    return commandLine.hasOption(OPTION_DEBUG);
  }

  private String getValue(String key) {
    if (commandLine.hasOption(key))
      return commandLine.getOptionValue(key);
    else
      return null;
  }
}
