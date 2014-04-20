package office.api.main.cybozu2ical;

import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Properties;

public class Config {

  private URI officeURL = null;
  private String username = null;
  private String password = null;
  private String keyitem = CBClient.KEYITEM_NAME;

  public Config(String file) {
    Properties props = null;
    try {
      props = new Properties();
      props.load(new FileReader(file));
    } catch (IOException e) {
      e.printStackTrace();
    }
    if (props != null) {
      try {
        officeURL = new URI(props.getProperty("officeURL").trim());
      } catch (URISyntaxException e) {
        e.printStackTrace();
      }
      if (props.containsKey("username")) {
        username = props.getProperty("username").trim();
        if (username.equals("")) {
          username = null;
        }
      }
      if (props.containsKey("password")) {
        password = props.getProperty("password").trim();
        if (password.equals("")) {
          password = null;
        }
      }
      if (props.containsKey("keyitem")) {
        if (props.getProperty("keyitem").trim().equals(CBClient.KEYITEM_ID)) {
          keyitem = CBClient.KEYITEM_ID;
        }
      }
    }
  }

  public URI getOfficeURL() {
    return officeURL;
  }

  public void setOfficeURL(URI officeURL) {
    this.officeURL = officeURL;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String getKeyitem() {
    return keyitem;
  }

  public void setKeyitem(String keyitem) {
    this.keyitem = keyitem;
  }
}
