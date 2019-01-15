//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2018 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.spi;

/**
 * A service provider interface that handles the storage of proxy credentials.
 */
public interface ProxyAuth
{
    /** Credentials for a proxy server. */
    public static class Credentials {
        public final String username;
        public final String password;
        public Credentials (String username, String password) {
            this.username = username;
            this.password = password;
        }
    }

    /**
     * Loads the credentials for the app installed in {@code appDir}.
     */
    public Credentials loadCredentials (String appDir);

    /**
     * Encrypts and saves the credentials for the app installed in {@code appDir}.
     */
    public void saveCredentials (String appDir, String username, String password);
}
