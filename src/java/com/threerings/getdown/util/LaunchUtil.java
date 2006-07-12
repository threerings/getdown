//
// $Id$
//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2006 Three Rings Design, Inc.
//
// This program is free software; you can redistribute it and/or modify it
// under the terms of the GNU General Public License as published by the Free
// Software Foundation; either version 2 of the License, or (at your option)
// any later version.
//
// This program is distributed in the hope that it will be useful, but WITHOUT
// ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
// FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
// more details.
//
// You should have received a copy of the GNU General Public License along with
// this program; if not, write to the: Free Software Foundation, Inc.,
// 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA

package com.threerings.getdown.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.commons.io.CopyUtils;

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
        return getJVMPath(false);
    }

    /**
     * Reconstructs the path to the JVM used to launch this process.
     *
     * @param windebug if true we will use java.exe instead of javaw.exe on
     * Windows.
     */
    public static String getJVMPath (boolean windebug)
    {
        String apbase = System.getProperty("java.home") +
            File.separator + "bin" + File.separator;
        String apath = apbase + "java";
        if (new File(apath).exists()) {
            return apath;
        }

        if (!windebug) {
            apath = apbase + "javaw.exe";
            if (new File(apath).exists()) {
                return apath;
            }
        }

        apath = apbase + "java.exe";
        if (new File(apath).exists()) {
            return apath;
        }

        Log.warning("Unable to find java! [jhome=" + apbase + "].");
        return apbase + "java";
    }

    /**
     * Upgrades Getdown by moving an installation managed copy of the Getdown
     * jar file over the non-managed copy (which would be used to run Getdown
     * itself).
     *
     * <p> If the upgrade fails for a variety of reasons, warnings are logged
     * but no other actions are taken. There's not much else one can do other
     * than try again next time around.
     */
    public static void upgradeGetdown (File oldgd, File curgd, File newgd)
    {
        // we assume getdown's jar file size changes with every upgrade, this
        // is not guaranteed, but in reality it will, and it allows us to avoid
        // pointlessly upgrading getdown every time the client is updated which
        // is unnecessarily flirting with danger
        if (!newgd.exists() || newgd.length() == curgd.length()) {
            return;
        }

        Log.info("Updating Getdown with " + newgd + "...");

        // clear out any old getdown
        if (oldgd.exists()) {
            oldgd.delete();
        }

        // now try updating using renames
        if (!curgd.exists() || curgd.renameTo(oldgd)) {
            if (newgd.renameTo(curgd)) {
                oldgd.delete(); // yay!
                try {
                    // copy the moved file back to getdown-dop-new.jar so that
                    // we don't end up downloading another copy next time
                    CopyUtils.copy(new FileInputStream(curgd),
                                   new FileOutputStream(newgd));
                } catch (IOException e) {
                    Log.warning("Error copying updated Getdown back: " + e);
                }
                return;
            }

            Log.warning("Unable to renameTo(" + oldgd + ").");
            // try to unfuck ourselves
            if (!oldgd.renameTo(curgd)) {
                Log.warning("Oh God, why dost thee scorn me so.");
            }
        }

        // that didn't work, let's try copying it
        Log.info("Attempting to upgrade by copying over " + curgd + "...");
        try {
            CopyUtils.copy(new FileInputStream(newgd),
                           new FileOutputStream(curgd));
        } catch (IOException ioe) {
            Log.warning("Mayday! Brute force copy method also failed.");
            Log.logStackTrace(ioe);
        }
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
