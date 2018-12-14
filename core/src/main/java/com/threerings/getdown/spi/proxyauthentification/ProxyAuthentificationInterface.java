
package com.threerings.getdown.spi.proxyauthentification;

public interface ProxyAuthentificationInterface
{
  public void encryptCredentials(String username, char[] password, String workingDir);

  public ProxyCredentials loadProxyCredentials(String workingDir);

}
