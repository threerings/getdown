
package com.threerings.getdown.util;

import static com.threerings.getdown.Log.log;

import java.net.InetSocketAddress;
import java.net.Proxy;

public class ConnectionProxyFactory
{

  private static boolean checkProxy(String aHttpProxyHost, String aHttpProxyPort,
      String aHttpsProxyHost, String aHttpsProxyPort)
  {
    return (!StringUtil.isBlank(aHttpProxyHost) && !StringUtil.isBlank(aHttpProxyPort)) ||
        (!StringUtil.isBlank(aHttpsProxyHost) && !StringUtil.isBlank(aHttpsProxyPort));
  }

  protected static Proxy getProxy()
  {
    String httpProxyHost = System.getProperty("http.proxyHost");
    String httpProxyPort = System.getProperty("http.proxyPort");
    String httpsProxyHost = System.getProperty("https.proxyHost");
    String httpsProxyPort = System.getProperty("https.proxyPort");
    Proxy proxy = Proxy.NO_PROXY;
    if (checkProxy(httpProxyHost, httpProxyPort, httpsProxyHost, httpsProxyPort))
    {
      proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(httpProxyHost, Integer.valueOf(
          httpProxyPort)));

      log.info("---------------- Proxy Info for Connection -----------------");
      log.info("-- Proxy Host: " + httpProxyHost);
      log.info("-- Proxy Port: " + httpProxyPort);
      log.info("---------------------------------------------");

    }
    return proxy;
  }
}
