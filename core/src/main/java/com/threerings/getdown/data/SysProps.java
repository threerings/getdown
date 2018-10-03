//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2016 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.data;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.threerings.getdown.util.VersionUtil;

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

    /** Configures the bootstrap appbase (used in lieu of providing a skeleton getdown.txt, and as
      * a last resort fallback). Usage: {@code -Dappbase=URL}. */
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

    /** Overrides enter {@code appbase}. Usage: {@code -Dappbase_override=URL}. */
    public static String appbaseOverride () {
        return System.getProperty("appbase_override");
    }

    /** If true, Getdown installs the app without ever bringing up a UI, except in the event of an
      * error. NOTE: it does not launch the app. See {@link #launchInSilent}.
      * Usage: {@code -Dsilent}. */
    public static boolean silent () {
        return System.getProperty("silent") != null;
    }

    /** If true, Getdown does not automatically install updates after downloading them. It waits
      * for the application to call `Getdown.install`.
      * Usage: {@code -Dno_install}. */
    public static boolean noInstall () {
     return System.getProperty("no_install") != null;
    }

    /** If true, Getdown installs the app without ever bringing up a UI and then launches it.
      * Usage: {@code -Dsilent=launch}. */
    public static boolean launchInSilent () {
        return "launch".equals(System.getProperty("silent"));
    }

    /** Specifies the delay (in minutes) to wait before starting the update and install process.
      * Minimum delay is 0 minutes, or no delay (negative values are rounded up to 0 minutes).
      * Maximum delay is 1 day, or 1440 minutes (larger values are rounded down to 1 day).
      * Usage: {@code -Ddelay=N}. */
    public static int startDelay () {
        return Math.min(Math.max(Integer.getInteger("delay", 0), 0), 60 * 24);
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

    /** Specifies the read timeout (in seconds) to use when downloading all files from the server.
      * The default is 30 seconds, meaning that if a download stalls for more than 30 seconds, the
      * update process wil fail. Setting the timeout to zero (or a negative value) will disable it.
      * Usage: {@code -Dread_timeout=N}. */
    public static int readTimeout () {
        return Integer.getInteger("read_timeout", 30);
    }

    /** Returns the number of threads used to perform digesting and verifying operations in
      * parallel. Usage: {@code -Dthread_pool_size=N} */
    public static int threadPoolSize () {
        return Integer.getInteger("thread_pool_size", Runtime.getRuntime().availableProcessors()-1);
    }

    /** Parses a Java version system property using the supplied regular expression. The numbers
      * extracted from the regexp will be placed in each consecutive hundreds position in the
      * returned value.
      *
      * <p>For example, {@code java.version} takes the form {@code 1.8.0_31}, and with the regexp
      * {@code (\d+)\.(\d+)\.(\d+)(_\d+)?} we would parse {@code 1, 8, 0, 31} and combine them into
      * the final value {@code 1080031}.
      *
      * <p>Note that non-numeric characters matched by the regular expression will simply be
      * ignored, and optional groups which do not match are treated as zero in the final version
      * calculation.
      *
      * <p>One can instead parse {@code java.runtime.version} which takes the form {@code
      * 1.8.0_31-b13}. Using regexp {@code (\d+)\.(\d+)\.(\d+)_(\d+)-b(\d+)} we would parse
      * {@code 1, 8, 0, 31, 13} and combine them into a final value {@code 108003113}.
      *
      * <p>Other (or future) JVMs may provide different version properties which can be parsed as
      * desired using this general scheme as long as the numbers appear from left to right in order
      * of significance.
      *
      * @throws IllegalArgumentException if no system named {@code propName} exists, or if
      * {@code propRegex} does not match the returned version string.
      */
    public static long parseJavaVersion (String propName, String propRegex) {
        String verstr = System.getProperty(propName);
        if (verstr == null) throw new IllegalArgumentException(
            "No system property '" + propName + "'.");

        long vers = VersionUtil.parseJavaVersion(propRegex, verstr);
        if (vers == 0L) throw new IllegalArgumentException(
            "Regexp '" + propRegex + "' does not match '" + verstr + "' (from " + propName + ")");
        return vers;
    }

    /**
     * Applies {@code appbase_override} or {@code appbase_domain} if they are set.
     */
    public static String overrideAppbase (String appbase) {
        String appbaseOverride = appbaseOverride();
        if (appbaseOverride != null) {
            return appbaseOverride;
        } else {
            return replaceDomain(appbase);
        }
    }

    /**
     * If appbase_domain property is set, replace the domain on the provided string.
     */
    public static String replaceDomain (String appbase)
    {
        String appbaseDomain = appbaseDomain();
        if (appbaseDomain != null) {
            Matcher m = Pattern.compile("(https?://[^/]+)(.*)").matcher(appbase);
            appbase = m.replaceAll(appbaseDomain + "$2");
        }
        return appbase;
    }
}
