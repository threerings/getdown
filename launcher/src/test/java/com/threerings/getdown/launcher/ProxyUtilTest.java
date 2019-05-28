//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2018 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.launcher;

import java.io.StringReader;
import java.net.URL;

import org.junit.Test;
import static org.junit.Assert.*;

public class ProxyUtilTest {

    static String[] strs (String... strs) { return strs; }

    static String[] findPAC (String code, String url) throws Exception {
        return ProxyUtil.findPACProxiesForURL(new StringReader(code), new URL(url));
    }

    static void testPAC (String code, String url, String... expectedProxies) throws Exception {
        assertArrayEquals(expectedProxies, findPAC(code, url));
    }

    @Test public void testPACProxy () throws Exception {
        String EXAMPLE0 =
            "function FindProxyForURL(url, host) {\n" +
            "  if (shExpMatch(host, '*.example.com')) { return 'DIRECT'; }\n" +
            "  if (isInNet(host, '10.0.0.0', '255.255.248.0')) {\n" +
            "    return 'PROXY fastproxy.example.com:8080';\n" +
            "  }\n" +
            "  return 'PROXY proxy.example.com:8080; DIRECT';\n" +
            "}\n";

        testPAC(EXAMPLE0, "http://test.example.com/", "DIRECT");
        testPAC(EXAMPLE0, "http://10.0.1.1/", "PROXY fastproxy.example.com:8080");
        testPAC(EXAMPLE0, "http://chicken.gov/", "PROXY proxy.example.com:8080", "DIRECT");

        String EXAMPLE1 =
            "function FindProxyForURL(url, host) {" +
            "    if (isPlainHostName(host) || dnsDomainIs(host, '.mozilla.org')) {" +
            "        return 'DIRECT';" +
            "    } else {" +
            "        return 'PROXY w3proxy.mozilla.org:8080; DIRECT';" +
            "    }" +
            "}";
        testPAC(EXAMPLE1, "http://test.example.com/", "PROXY w3proxy.mozilla.org:8080", "DIRECT");
        testPAC(EXAMPLE1, "http://www.mozilla.org/", "DIRECT");
        testPAC(EXAMPLE1, "http://foo.mozilla.org/", "DIRECT");
        testPAC(EXAMPLE1, "http://localhost/", "DIRECT");

        String EXAMPLE2 =
            "    function FindProxyForURL(url, host) {\n" +
            "        if ((isPlainHostName(host) || dnsDomainIs(host, '.mozilla.org')) &&\n" +
            "            !localHostOrDomainIs(host, 'www.mozilla.org') &&\n" +
            "            !localHostOrDomainIs(host, 'merchant.mozilla.org')) {\n" +
            "            return 'DIRECT';\n" +
            "        } else {\n" +
            "            return 'PROXY w3proxy.mozilla.org:8080; DIRECT';\n" +
            "        }\n" +
            "    }";
        testPAC(EXAMPLE2, "http://test.example.com/", "PROXY w3proxy.mozilla.org:8080", "DIRECT");
        testPAC(EXAMPLE2, "http://www.mozilla.org/", "PROXY w3proxy.mozilla.org:8080", "DIRECT");
        testPAC(EXAMPLE2, "http://www/", "PROXY w3proxy.mozilla.org:8080", "DIRECT");
        testPAC(EXAMPLE2, "http://foo.mozilla.org/", "DIRECT");
        testPAC(EXAMPLE2, "http://localhost/", "DIRECT");

        String EXAMPLE3A =
            "function FindProxyForURL(url, host) {\n" +
            "    if (isResolvable(host)) return 'DIRECT';\n" +
            "    else return 'PROXY proxy.mydomain.com:8080';\n" +
            "}";
        testPAC(EXAMPLE3A, "http://www.mozilla.org/", "DIRECT");
        testPAC(EXAMPLE3A, "http://doesnotexist.mozilla.org/", "PROXY proxy.mydomain.com:8080");

        String EXAMPLE3B =
            "function FindProxyForURL(url, host) {\n" +
            "    if (isPlainHostName(host) ||\n" +
            "        dnsDomainIs(host, '.mydomain.com') ||\n" +
            "        isResolvable(host)) {\n" +
            "        return 'DIRECT';\n" +
            "    } else {\n" +
            "        return 'PROXY proxy.mydomain.com:8080';\n" +
            "    }\n" +
            "}";
        testPAC(EXAMPLE3B, "http://plain/", "DIRECT");
        testPAC(EXAMPLE3B, "http://foo.mydomain.com/", "DIRECT");
        testPAC(EXAMPLE3B, "http://www.mozilla.org/", "DIRECT");
        testPAC(EXAMPLE3B, "http://doesnotexist.mozilla.org/", "PROXY proxy.mydomain.com:8080");

        // example 4
        // function FindProxyForURL(url, host) {
        //     if (isInNet(host, '198.95.0.0', '255.255.0.0')) return 'DIRECT';
        //     else return 'PROXY proxy.mydomain.com:8080';
        // }
        // function FindProxyForURL(url, host) {
        //     if (isPlainHostName(host) ||
        //         dnsDomainIs(host, '.mydomain.com') ||
        //         isInNet(host, '198.95.0.0', '255.255.0.0')) {
        //         return 'DIRECT';
        //     } else {
        //         return 'PROXY proxy.mydomain.com:8080';
        //     }
        // }

        // example 5
        // function FindProxyForURL(url, host) {
        //     if (isPlainHostName(host) || dnsDomainIs(host, '.mydomain.com')) return 'DIRECT';
        //     else if (shExpMatch(host, '*.com')) return 'PROXY proxy1.mydomain.com:8080; ' +
        //         'PROXY proxy4.mydomain.com:8080';
        //     else if (shExpMatch(host, '*.edu')) return 'PROXY proxy2.mydomain.com:8080; ' +
        //         'PROXY proxy4.mydomain.com:8080';
        //     else return 'PROXY proxy3.mydomain.com:8080; ' +
        //         'PROXY proxy4.mydomain.com:8080';
        // }

        // example 6
        // function FindProxyForURL(url, host) {
        //     if (url.substring(0, 5) == 'http:') {
        //         return 'PROXY http-proxy.mydomain.com:8080';
        //     }
        //     else if (url.substring(0, 4) == 'ftp:') {
        //         return 'PROXY ftp-proxy.mydomain.com:8080';
        //     }
        //     else if (url.substring(0, 7) == 'gopher:') {
        //         return 'PROXY gopher-proxy.mydomain.com:8080';
        //     }
        //     else if (url.substring(0, 6) == 'https:' ||
        //              url.substring(0, 6) == 'snews:') {
        //         return 'PROXY security-proxy.mydomain.com:8080';
        //     } else {
        //         return 'DIRECT';
        //     }
        // }
    }
}
