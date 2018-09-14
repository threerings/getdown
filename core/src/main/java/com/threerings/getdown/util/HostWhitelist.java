//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2016 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.util;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
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
    public static final HostWhitelist INSTANCE = new HostWhitelist();

    private final List<String> _hosts;

    HostWhitelist ()
    {
        this(Build.hostWhitelist());
    }

    HostWhitelist (List<String> hosts)
    {
        _hosts = Collections.unmodifiableList(hosts);
    }

    /**
     * Verifies that the specified URL should be accessible, per this host whitelist.
     * If the URL should not be accessible, this method throws a {@link MalformedURLException}.
     * If the URL should be accessible, this method simply returns the {@link URL} passed in.
     */
    public final URL verify (URL url) throws MalformedURLException
    {
        if (url == null || _hosts.isEmpty()) {
            // either there is no URL to validate or no whitelist was configured
            return url;
        }

        String urlHost = url.getHost();

        for (String host : _hosts) {
            String regex = host.replace(".", "\\.").replace("*", ".*");
            if (urlHost.matches(regex)) {
                return url;
            }
        }

        throw new MalformedURLException("The host for the specified URL (" + url
            + ") is not in the host whitelist: " + _hosts);
    }

    /** {@inheritDoc} */
    @Override
    public String toString()
    {
        return _hosts.toString();
    }
}
