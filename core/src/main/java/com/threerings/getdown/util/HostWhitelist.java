//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2016 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.util;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import com.threerings.getdown.data.Build;

/**
 * Optional support for compiling a URL host whitelist into the Getdown JAR.
 * Useful if you're on the paranoid end of the security spectrum.
 *
 * @see Build#hostWhitelist()
 */
public final class HostWhitelist
{
    /**
     * Verifies that the specified URL should be accessible, per the built-in host whitelist.
     * See {@link Build#hostWhitelist()} and {@link #verify(List,URL)}.
     */
    public static URL verify (URL url) throws MalformedURLException
    {
        return verify(Build.hostWhitelist(), url);
    }

    /**
     * Verifies that the specified URL should be accessible, per the supplied host whitelist.
     * If the URL should not be accessible, this method throws a {@link MalformedURLException}.
     * If the URL should be accessible, this method simply returns the {@link URL} passed in.
     */
    public static URL verify (List<String> hosts, URL url) throws MalformedURLException
    {
        if (url == null || hosts.isEmpty()) {
            // either there is no URL to validate or no whitelist was configured
            return url;
        }

        String urlHost = url.getHost();
        for (String host : hosts) {
            String regex = host.replace(".", "\\.").replace("*", ".*");
            if (urlHost.matches(regex)) {
                return url;
            }
        }

        throw new MalformedURLException(
            "The host for the specified URL (" + url + ") is not in the host whitelist: " + hosts);
    }
}
