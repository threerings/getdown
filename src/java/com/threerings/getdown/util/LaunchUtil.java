//
// $Id: LaunchUtil.java,v 1.1 2004/08/03 03:29:58 mdb Exp $

package com.threerings.getdown.util;

import java.io.File;

import com.threerings.getdown.Log;

/**
 * Useful routines for launching Java applications from within other Java
 * applications.
 */
public class LaunchUtil
{
    /**
     * Reconstructs the path to the JVM used to launch this process.
     */
    public static String getJVMPath ()
    {
        String apbase = System.getProperty("java.home") +
            File.separator + "bin" + File.separator;
        String apath = apbase + "java";
        if (!new File(apath).exists()) {
            apath = apbase + "javaw.exe";
            if (!new File(apath).exists()) {
                apath = apbase + "java.exe";
                if (!new File(apath).exists()) {
                    Log.warning("Unable to find java! [jhome=" + apbase + "].");
                    apath = apbase + "java";
                }
            }
        }
        return apath;
    }

    /**
     * Returns true if, on this operating system, we have to stick around
     * and read the stderr from our children processes to prevent them
     * from filling their output buffers and hanging.
     */
    public static boolean mustMonitorChildren ()
    {
        String osname = System.getProperty("os.name").toLowerCase();
        return (osname.indexOf("windows 98") != -1 ||
                osname.indexOf("windows me") != -1);
    }
}
