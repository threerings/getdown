//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2018 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.net;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.threerings.getdown.data.SysProps;
import com.threerings.getdown.util.Base64;
import com.threerings.getdown.util.StreamUtil;

/**
 * Manages the process of making HTTP connections, using a proxy if necessary. Tracks and reports
 * when proxy credentials are rejected.
 */
public class Connector {

    /** The default connector uses no proxy. */
    public static final Connector DEFAULT = new Connector(Proxy.NO_PROXY);

    /** Tracks the state of a connector. If it fails for proxy-related reasons, it may transition
      * to a need_proxy or need_proxy_auth state. */
    public enum State { ACTIVE, NEED_PROXY, NEED_PROXY_AUTH }

    /** The proxy used by this connector. */
    public final Proxy proxy;

    /** The current state of this connector. */
    public State state = State.ACTIVE;

    public Connector (Proxy proxy) {
        this.proxy = proxy;
    }

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
    public URLConnection open (URL url, int connectTimeout, int readTimeout)
        throws IOException
    {
        URLConnection conn = url.openConnection(proxy);

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
    public HttpURLConnection openHttp (URL url, int connectTimeout, int readTimeout)
        throws IOException
    {
        return (HttpURLConnection)open(url, connectTimeout, readTimeout);
    }

    /**
     * Downloads {@code url} into {@code target}.
     */
    public void download (URL url, File target) throws IOException {
        URLConnection conn = open(url, 0, 0);
        // we have to tell Java not to use caches here, otherwise it will cache any request for
        // same URL for the lifetime of this JVM (based on the URL string, not the URL object);
        // if the getdown.txt file, for example, changes in the meanwhile, we would never hear
        // about it; turning off caches is not a performance concern, because when Getdown asks
        // to download a file, it expects it to come over the wire, not from a cache
        conn.setUseCaches(false);
        conn.setRequestProperty("Accept-Encoding", "gzip");
        checkConnectOK(conn, "Unable to download " + url);
        try (InputStream fin = conn.getInputStream()) {
            String encoding = conn.getContentEncoding();
            boolean gzip = "gzip".equalsIgnoreCase(encoding);
            try (InputStream fin2 = (gzip ? new GZIPInputStream(fin) : fin)) {
                try (FileOutputStream fout = new FileOutputStream(target)) {
                    StreamUtil.copy(fin2, fout);
                }
            }
        }
    }

    /**
     * Fetches the data at {@code url} into a string.
     */
    public String fetch (URL url) throws IOException {
        URLConnection conn = open(url, 0, 0);
        checkConnectOK(conn, "Unable to fetch " + url);
        int size = conn.getContentLength();
        ByteArrayOutputStream out = new ByteArrayOutputStream(size > 0 ? size : 1024);
        try (InputStream in = conn.getInputStream()) {
            StreamUtil.copy(in, out);
        }
        return out.toString(UTF_8.toString());
    }

    /**
     * Checks that {@code conn} returned an {@code OK} response code iff it is an HTTP connection.
     * If the connection failed for proxy related reasons, this changes the state of this connector
     * to reflect the needed proxy information.
     */
    public void checkConnectOK (URLConnection conn, String errpre) throws IOException
    {
        // if it's not an HTTP connection, there's nothing to check
        if (!(conn instanceof HttpURLConnection)) return;

        int code = ((HttpURLConnection)conn).getResponseCode();
        switch (code) {
        case HttpURLConnection.HTTP_OK:
            return;
        case HttpURLConnection.HTTP_FORBIDDEN:
        case HttpURLConnection.HTTP_USE_PROXY:
            state = State.NEED_PROXY;
            break;
        case HttpURLConnection.HTTP_PROXY_AUTH:
            state = State.NEED_PROXY_AUTH;
            break;
        }
        throw new IOException(errpre + " [code=" + code + "]");
    }

    /**
     * Adds appropriate proxy args from this connector's configuration to the supplied list of
     * command line args that will be used to launch the app.
     */
    public void addProxyArgs (List<String> args) {
        if (proxy.type() == Proxy.Type.HTTP && proxy.address() instanceof InetSocketAddress) {
            InetSocketAddress proxyAddr = (InetSocketAddress) proxy.address();
            String proxyHost = proxyAddr.getHostString();
            int proxyPort = proxyAddr.getPort();
            args.add("-Dhttp.proxyHost=" + proxyHost);
            args.add("-Dhttp.proxyPort=" + proxyPort);
            args.add("-Dhttps.proxyHost=" + proxyHost);
            args.add("-Dhttps.proxyPort=" + proxyPort);
        }
    }
}
