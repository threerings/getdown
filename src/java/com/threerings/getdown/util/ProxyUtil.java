//
// $Id: ProxyUtil.java,v 1.2 2004/07/29 18:46:57 mdb Exp $

package com.threerings.getdown.util;

import java.net.URL;

import com.threerings.getdown.Log;

/**
 * Code to detect a user's proxy settings.
 */
public class ProxyUtil
{
    /**
     * Detects whether or not there are proxy settings that should be used
     * by this JVM.
     *
     * @return true if there are such settings, false if not.
     */
    public static boolean detectProxy (URL sampleURL)
    {
        // first look for a proxy that's already set
        if ((_proxyIP = System.getProperty("proxyHost")) != null) {
            _proxyPort = System.getProperty("proxyPort");
            return true;
        }
        if ((_proxyIP = System.getProperty("http.proxyHost")) != null) {
            _proxyPort = System.getProperty("http.proxyPort");
            return true;
        }

        try {
            //  Look around for the 1.4.X plugin proxy detection
            //  class... Without it, cannot autodetect...
            Class t = Class.forName("com.sun.java.browser.net.ProxyService");
            com.sun.java.browser.net.ProxyInfo[] pi =
                com.sun.java.browser.net.ProxyService.getProxyInfo(sampleURL);
            if (pi == null || pi.length == 0) {
                Log.info("1.4.X reported NULL proxy (no proxy assumed).");
                return false;

            } else {
                Log.info("1.4.X Proxy info getProxy: " + pi[0].getHost() +
                         " getPort: " + pi[0].getPort() +
                         " isSocks: " + pi[0].isSocks());
                _proxyIP = pi[0].getHost();
                _proxyPort = "" + pi[0].getPort();
                Log.info("Detected proxy " + _proxyIP + " port " + _proxyPort);
                return true;
            }

        } catch (Exception ee) {
            Log.info("Sun Plugin 1.4.X proxy detection class not " +
                     "found: " + ee + ". Trying failover detection...");
        }

        try {
            String key = "javaplugin.proxy.config.list";
            String proxyList = (String)
                System.getProperties().getProperty(key);
            proxyList = proxyList.toUpperCase();
            Log.info("Plugin proxy config list: " + proxyList);

            //  Using HTTP proxy as proxy for HTTP proxy tunnelled SSL
            //  socket (should be listed FIRST)....
            if (proxyList != null) {
                // 6.0.0 1/14/03 1.3.1_06 appears to omit HTTP portion of
                // 6.0.reported proxy list... Mod to accomodate this...

                // Expecting proxyList of "HTTP=XXX.XXX.XXX.XXX:Port"
                // OR "XXX.XXX.XXX.XXX:Port" & assuming HTTP...
                if (proxyList.indexOf("HTTP=") > -1) {
                    _proxyIP = proxyList.substring(
                        proxyList.indexOf("HTTP=") + 5,
                        proxyList.indexOf(":"));
                } else {
                    _proxyIP = proxyList.substring(
                        0, proxyList.indexOf(":"));
                }
                int endOfPort = proxyList.indexOf(",");
                if (endOfPort < 1) {
                    endOfPort = proxyList.length();
                }
                _proxyPort = proxyList.substring(proxyList.indexOf(":") + 1,
                                                 endOfPort);
                Log.info("Detected proxy " + _proxyIP + " port " + _proxyPort);
                return true;
            }

        } catch (Exception e) {
            Log.warning("Exception during failover auto proxy detect: " + e);
        }

        return false;
    }

    /**
     * Returns the IP of the detected proxy. This is only valid after a
     * call to {@link #detectProxy} that returns true.
     */
    public static String getProxyIP ()
    {
        return _proxyIP;
    }

    /**
     * Returns the port of the detected proxy. This is only valid after a
     * call to {@link #detectProxy} that returns true.
     */
    public static String getProxyPort ()
    {
        return _proxyPort;
    }

    /**
     * Sets the necessary system properties to enact the use of this proxy
     * host and port.
     */
    public static void configureProxy (String host, String port)
    { 
        System.setProperty("http.proxyHost", host);
        System.setProperty("http.proxyPort", port);
   }

    protected static String _proxyIP;
    protected static String _proxyPort = "80";
    protected static boolean _useProxy = false;
}
