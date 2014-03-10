//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2014 Three Rings Design, Inc.
// https://raw.github.com/threerings/getdown/master/LICENSE

package com.threerings.getdown.data;

/**
 * This class encapsulates all system properties that are read and processed by Getdown. Don't
 * stick a call to {@code System.getProperty} randomly into the code, put it in here and give it an
 * accessor so that it's easy to see all of the secret system property arguments that Getdown makes
 * use of.
 */
public class SysProps
{
    /** Configures the appdir (in lieu of passing it in argv). Usage: {@code -Dappdir=foo}. */
    public static String appDir () {
        return System.getProperty("appdir");
    }

    /** Configures the appid (in lieu of passing it in argv). Usage: {@code -Dappid=foo}. */
    public static String appId () {
        return System.getProperty("appid");
    }

    /** Configures the appbase (in lieu of providing a skeleton getdown.txt, and as a last resort
     * fallback). Usage: {@code -Dappbase=someurl}. */
    public static String appBase () {
        return System.getProperty("appbase");
    }

    /** If true, disables redirection of logging into {@code launcher.log}.
     * Usage: {@code -Dno_log_redir}. */
    public static boolean noLogRedir () {
        return System.getProperty("no_log_redir") != null;
    }

    /** Overrides the domain on {@code appbase}. Usage: {@code -Dappbase_domain=foo}. */
    public static String appbaseDomain () {
        return System.getProperty("appbase_domain");
    }

    /** If true, Getdown installs the app without ever bringing up a UI, except in the event of an
     * error. NOTE: it does not launch the app. See {@link #launchInSilent}.
     * Usage: {@code -Dsilent}. */
    public static boolean silent () {
        return System.getProperty("silent") != null;
    }

    /** If true, Getdown installs the app without ever bringing up a UI and then launches it.
     * Usage: {@code -Dsilent=launch}. */
    public static boolean launchInSilent () {
        return "launch".equals(System.getProperty("silent"));
    }

    /** Specifies the a delay (in minutes) to wait before starting the update and install process.
     * Usage: {@code -Ddelay=N}. */
    public static int startDelay () {
        return Integer.getInteger("delay", 0);
    }

    /** If true, Getdown will not unpack {@code uresource} jars. Usage: {@code -Dno_unpack}. */
    public static boolean noUnpack () {
        return Boolean.getBoolean("no_unpack");
    }

    /** If true, Getdown will run the application in the same VM in which Getdown is running. If
     * false (the default), Getdown will fork a new VM. Note that reusing the same VM prevents
     * Getdown from configuring some launch-time-only VM parameters (like -mxN etc.).
     * Usage: {@code -Ddirect}. */
    public static boolean direct () {
        return Boolean.getBoolean("direct");
    }

    /** Specifies the connection timeout (in seconds) to use when downloading control files from
     * the server. This is chiefly useful when you are running in versionless mode and want Getdown
     * to more quickly timeout its startup update check if the server with which it is
     * communicating is not available. Usage: {@code -Dconnect_timeout=N}. */
    public static int connectTimeout () {
        return Integer.getInteger("connect_timeout", 0);
    }
}
