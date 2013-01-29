//
// $Id$
//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2010 Three Rings Design, Inc.
// http://code.google.com/p/getdown/
//
// Redistribution and use in source and binary forms, with or without modification, are permitted
// provided that the following conditions are met:
//
// 1. Redistributions of source code must retain the above copyright notice, this list of
//    conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright notice, this list of
//    conditions and the following disclaimer in the documentation and/or other materials provided
//    with the distribution.
//
// THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES,
// INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
// PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT,
// INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
// TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
// LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
// SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

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
}
