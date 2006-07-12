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


package com.threerings.getdown.launcher;

import java.awt.Component;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.Image;
import java.net.URL;

import javax.swing.JApplet;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import java.io.IOException;
import java.io.FileOutputStream;
import java.io.File;
import java.io.PrintStream;

import com.samskivert.util.RunAnywhere;
import com.samskivert.util.StringUtil;

import com.threerings.getdown.Log;

/**
 * An applet that can be used to launch a Getdown application (when signed and
 * given privileges).
 */
public class GetdownApplet extends JApplet
{
    @Override // documentation inherited
    public void init ()
    {
        // when run from an applet, we install 
        String root;
        if (RunAnywhere.isWindows()) {
            root = "Application Data";
        } else if (RunAnywhere.isMacOS()) {
            root = "Library" + File.separator + "Application Support";
        } else /* isLinux() or something wacky */ {
            root = ".getdown";
        }

        String appname = getParameter("appname");
        String appdir = System.getProperty("user.home") +
            File.separator + root + File.separator + appname;

        // if our application directory does not exist, auto-create it
        File appDir = new File(appdir);
        if (!appDir.exists() || !appDir.isDirectory()) {
            if (!appDir.mkdirs()) {
                Log.warning("Unable to create app_dir '" + appdir + "'.");
                // TODO: report error
                return;
            }
        }

        // if an installer.txt file is desired, create that
        String inststr = getParameter("installer");
        if (!StringUtil.isBlank(inststr)) {
            File infile = new File(appDir, "installer.txt");
            if (!infile.exists()) {
                writeToFile(infile, inststr);
            }
        }

        // if our getdown.txt file does not exist, auto-create it
        File gdfile = new File(appDir, "getdown.txt");
        if (!gdfile.exists()) {
            String appbase = getParameter("appbase");
            if (StringUtil.isBlank(appbase)) {
                Log.warning("Missing 'appbase' cannot auto-create " +
                            "application directory.");
                // TODO: report
                return;
            }
            if (!writeToFile(gdfile, "appbase = " + appbase)) {
                // TODO: report the error
                return;
            }
        }

        // if a background image was specified, grabbit
        String imgpath = getParameter("bgimage");
        try {
            if (!StringUtil.isBlank(imgpath)) {
                _bgimage = getImage(new URL(getDocumentBase(), imgpath));
            }
        } catch (Exception e) {
            Log.info("Failed to load background image [path=" + imgpath + "].");
            Log.logStackTrace(e);
        }

        // record a few things for posterity
        Log.info("------------------ VM Info ------------------");
        Log.info("-- OS Name: " + System.getProperty("os.name"));
        Log.info("-- OS Arch: " + System.getProperty("os.arch"));
        Log.info("-- OS Vers: " + System.getProperty("os.version"));
        Log.info("-- Java Vers: " + System.getProperty("java.version"));
        Log.info("-- Java Home: " + System.getProperty("java.home"));
        Log.info("-- User Name: " + System.getProperty("user.name"));
        Log.info("-- User Home: " + System.getProperty("user.home"));
        Log.info("-- Cur dir: " + System.getProperty("user.dir"));
        Log.info("---------------------------------------------");

        try {
            _getdown = new Getdown(appDir, null) {
                protected Container createContainer () {
                    getContentPane().removeAll();
                    return getContentPane();
                }
                protected void showContainer () {
                    ((JPanel)getContentPane()).revalidate();
                }
                protected void disposeContainer () {
                    // nothing to do as we're in an applet
                }
                protected boolean invokeDirect () {
                    return "true".equalsIgnoreCase(getParameter("direct"));
                }
                protected JApplet getApplet () {
                    return GetdownApplet.this;
                }
                protected Image getBackgroundImage () {
                    return _bgimage == null ?
                        super.getBackgroundImage() : _bgimage;
                }
                protected void exit (int exitCode) {
                    // don't exit as we're in an applet
                }
            };

            // set up our user interface immediately
            _getdown.preInit();

        } catch (Exception e) {
            Log.logStackTrace(e);
        }
    }

    @Override // documentation inherited
    public void start ()
    {
        try {
            _getdown.start();
        } catch (Exception e) {
            Log.logStackTrace(e);
        }
    }

    @Override // documentation inherited
    public void stop ()
    {
        // TODO
    }

    /**
     * Creates the specified file and writes the supplied contents to it.
     */
    protected boolean writeToFile (File tofile, String contents)
    {
        try {
            PrintStream out = new PrintStream(new FileOutputStream(tofile));
            out.println(contents);
            out.close();
            return true;
        } catch (IOException ioe) {
            Log.warning("Failed to create '" + tofile + "'.");
            Log.logStackTrace(ioe);
            return false;
        }
    }

    protected Getdown _getdown;
    protected Image _bgimage;
}
