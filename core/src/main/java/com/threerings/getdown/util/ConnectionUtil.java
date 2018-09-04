//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2016 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.util;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import com.threerings.getdown.data.SysProps;
import com.threerings.getdown.util.Base64;

import static java.nio.charset.StandardCharsets.UTF_8;

public class ConnectionUtil
{
    /**
     * Opens a connection to a URL, setting the authentication header if user info is present.
     * @param url the URL to which to open a connection.
     * @param connectTimeout if {@code > 0} then a timeout, in seconds, to use when opening the
     * connection. If {@code 0} is supplied, the connection timeout specified via system properties
     * will be used instead.
     * @param readTimeout if {@code > 0} then a timeout, in seconds, to use while reading data from
     * the connection. If {@code 0} is supplied, the read timeout specified via system properties
     * will be used instead.
     */
    public static URLConnection open (URL url, int connectTimeout, int readTimeout)
        throws IOException
    {
        URLConnection conn = url.openConnection();

        // configure a connect timeout, if requested
        int ctimeout = connectTimeout > 0 ? connectTimeout : SysProps.connectTimeout();
        if (ctimeout > 0) {
            conn.setConnectTimeout(ctimeout * 1000);
        }

        // configure a read timeout, if requested
        int rtimeout = readTimeout > 0 ? readTimeout : SysProps.readTimeout();
        if (rtimeout > 0) {
            conn.setReadTimeout(rtimeout * 1000);
        }

        // If URL has a username:password@ before hostname, use HTTP basic auth
        String userInfo = url.getUserInfo();
        if (userInfo != null) {
            // Remove any percent-encoding in the username/password
            userInfo = URLDecoder.decode(userInfo, "UTF-8");
            // Now base64 encode the auth info and make it a single line
            String encoded = Base64.encodeToString(userInfo.getBytes(UTF_8), Base64.DEFAULT).
                replaceAll("\\n","").replaceAll("\\r", "");
            conn.setRequestProperty("Authorization", "Basic " + encoded);
        }

        return conn;
    }

    /**
     * Opens a connection to a http or https URL, setting the authentication header if user info is
     * present. Throws a class cast exception if the connection returned is not the right type. See
     * {@link #open} for parameter documentation.
     */
    public static HttpURLConnection openHttp (URL url, int connectTimeout, int readTimeout)
        throws IOException
    {
        return (HttpURLConnection)open(url, connectTimeout, readTimeout);
    }
}
