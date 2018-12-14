
package com.threerings.getdown.launcher;

import java.net.Authenticator;
import java.net.PasswordAuthentication;

public class ProxyAuthenticator extends Authenticator
{

  private String userName;
  private char[] password;

  @Override
  protected PasswordAuthentication getPasswordAuthentication()
  {
    return new PasswordAuthentication(userName, password);
  }

  public ProxyAuthenticator(String userName, char[] password)
  {
    this.userName = userName;
    this.password = password;
  }

}
