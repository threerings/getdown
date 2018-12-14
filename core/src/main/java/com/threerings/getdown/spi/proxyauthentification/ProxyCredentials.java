
package com.threerings.getdown.spi.proxyauthentification;

public class ProxyCredentials
{
  private String user;

  private String password;

  // -------------------------------------------------------------------

  public ProxyCredentials(String aUser, String aPassword)
  {
    user = aUser;
    password = aPassword;
  }

  // -------------------------------------------------------------------

  public String getUser()
  {
    return user;
  }


  // -------------------------------------------------------------------

  public String getPassword()
  {
    return password;
  }


}
