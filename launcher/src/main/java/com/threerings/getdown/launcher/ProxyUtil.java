//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2018 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.launcher;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.util.Iterator;
import java.util.ServiceLoader;

import ca.beq.util.win32.registry.RegistryKey;
import ca.beq.util.win32.registry.RegistryValue;
import ca.beq.util.win32.registry.RootKey;

import com.threerings.getdown.data.Application;
import com.threerings.getdown.spi.ProxyAuth;
import com.threerings.getdown.util.Config;
import com.threerings.getdown.util.ConnectionUtil;
import com.threerings.getdown.util.LaunchUtil;
import com.threerings.getdown.util.StringUtil;

import static com.threerings.getdown.Log.log;

public class ProxyUtil {

    public static boolean autoDetectProxy (Application app)
    {
        String host = null, port = null;

        // check for a proxy configured via system properties
        if (System.getProperty("https.proxyHost") != null) {
            host = System.getProperty("https.proxyHost");
            port = System.getProperty("https.proxyPort");
        }
        if (StringUtil.isBlank(host) && System.getProperty("http.proxyHost") != null) {
            host = System.getProperty("http.proxyHost");
            port = System.getProperty("http.proxyPort");
        }

        // check the Windows registry
        if (StringUtil.isBlank(host) && LaunchUtil.isWindows()) {
            try {
                String rhost = null, rport = null;
                boolean enabled = false;
                RegistryKey.initialize();
                RegistryKey r = new RegistryKey(RootKey.HKEY_CURRENT_USER, PROXY_REGISTRY);
                for (Iterator<?> iter = r.values(); iter.hasNext(); ) {
                    RegistryValue value = (RegistryValue)iter.next();
                    if (value.getName().equals("ProxyEnable")) {
                        enabled = value.getStringValue().equals("1");
                    }
                    if (value.getName().equals("ProxyServer")) {
                        String strval = value.getStringValue();
                        int cidx = strval.indexOf(":");
                        if (cidx != -1) {
                            rport = strval.substring(cidx+1);
                            strval = strval.substring(0, cidx);
                        }
                        rhost = strval;
                    }
                }
                if (enabled) {
                    host = rhost;
                    port = rport;
                } else {
                    log.info("Detected no proxy settings in the registry.");
                }
            } catch (Throwable t) {
                log.info("Failed to find proxy settings in Windows registry", "error", t);
            }
        }

        // look for a proxy.txt file
        if (StringUtil.isBlank(host)) {
            String[] hostPort = loadProxy(app);
            host = hostPort[0];
            port = hostPort[1];
        }

        if (StringUtil.isBlank(host)) {
            return false;
        }

        // yay, we found a proxy configuration, configure it in the app
        initProxy(app, host, port, null, null);
        return true;
    }

    public static boolean canLoadWithoutProxy (URL rurl)
    {
        log.info("Testing whether proxy is needed, via: " + rurl);
        try {
            // try to make a HEAD request for this URL (use short connect and read timeouts)
            URLConnection conn = ConnectionUtil.open(Proxy.NO_PROXY, rurl, 5, 5);
            if (conn instanceof HttpURLConnection) {
                HttpURLConnection hcon = (HttpURLConnection)conn;
                try {
                    hcon.setRequestMethod("HEAD");
                    hcon.connect();
                    // make sure we got a satisfactory response code
                    int rcode = hcon.getResponseCode();
                    if (rcode == HttpURLConnection.HTTP_PROXY_AUTH ||
                        rcode == HttpURLConnection.HTTP_FORBIDDEN) {
                        log.warning("Got an 'HTTP credentials needed' response", "code", rcode);
                    } else {
                        return true;
                    }
                } finally {
                    hcon.disconnect();
                }
            } else {
                // if the appbase is not an HTTP/S URL (like file:), then we don't need a proxy
                return true;
            }
        } catch (IOException ioe) {
            log.info("Failed to HEAD " + rurl + ": " + ioe);
            log.info("We probably need a proxy, but auto-detection failed.");
        }
        return false;
    }

    public static void configProxy (Application app, String host, String port,
                                    String username, String password) {
        // save our proxy host and port in a local file
        saveProxy(app, host, port);

        // save our credentials via the SPI
        if (!StringUtil.isBlank(username) && !StringUtil.isBlank(password)) {
            ServiceLoader<ProxyAuth> loader = ServiceLoader.load(ProxyAuth.class);
            Iterator<ProxyAuth> iterator = loader.iterator();
            String appDir = app.getAppDir().getAbsolutePath();
            while (iterator.hasNext()) {
                iterator.next().saveCredentials(appDir, username, password);
            }
        }

        // also configure them in the app
        initProxy(app, host, port, username, password);
    }

    public static String[] loadProxy (Application app) {
        File pfile = app.getLocalPath("proxy.txt");
        if (pfile.exists()) {
            try {
                Config pconf = Config.parseConfig(pfile, Config.createOpts(false));
                return new String[] { pconf.getString("host"), pconf.getString("port") };
            } catch (IOException ioe) {
                log.warning("Failed to read '" + pfile + "': " + ioe);
            }
        }
        return new String[] { null, null};
    }

    public static void saveProxy (Application app, String host, String port) {
        File pfile = app.getLocalPath("proxy.txt");
        try (PrintStream pout = new PrintStream(new FileOutputStream(pfile))) {
            if (!StringUtil.isBlank(host)) {
                pout.println("host = " + host);
            }
            if (!StringUtil.isBlank(port)) {
                pout.println("port = " + port);
            }
        } catch (IOException ioe) {
            log.warning("Error creating proxy file '" + pfile + "': " + ioe);
        }
    }

    public static void initProxy (Application app, String host, String port,
                                  String username, String password)
    {
        // check whether we have saved proxy credentials
        String appDir = app.getAppDir().getAbsolutePath();
        ServiceLoader<ProxyAuth> loader = ServiceLoader.load(ProxyAuth.class);
        Iterator<ProxyAuth> iter = loader.iterator();
        ProxyAuth.Credentials creds = iter.hasNext() ? iter.next().loadCredentials(appDir) : null;
        if (creds != null) {
            username = creds.username;
            password = creds.password;
        }
        boolean haveCreds = !StringUtil.isBlank(username) && !StringUtil.isBlank(password);

        int pport = StringUtil.isBlank(port) ? 80 : Integer.valueOf(port);
        log.info("Using proxy", "host", host, "port", pport, "haveCreds", haveCreds);
        app.proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, pport));

        if (haveCreds) {
            final String fuser = username;
            final char[] fpass = password.toCharArray();
            Authenticator.setDefault(new Authenticator() {
                @Override protected PasswordAuthentication getPasswordAuthentication () {
                    return new PasswordAuthentication(fuser, fpass);
                }
            });
        }
    }

    protected static final String PROXY_REGISTRY =
        "Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings";
}
