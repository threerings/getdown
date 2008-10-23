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

import java.awt.Container;
import java.awt.event.WindowEvent;
import java.awt.event.WindowAdapter;
import javax.swing.JFrame;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.BufferedOutputStream;
import java.io.PrintStream;

import com.samskivert.swing.util.SwingUtil;
import com.samskivert.util.StringUtil;

import static com.threerings.getdown.Log.log;

/**
 * Does something extraordinary.
 */
public class GetdownApp
{
    public static void main (String[] args)
    {
        // maybe they specified the appdir in a system property
        int aidx = 0;
        String adarg = System.getProperty("appdir");
        // if not, check for a command line argument
        if (StringUtil.isBlank(adarg)) {
            if (args.length < 1) {
                System.err.println(
                    "Usage: java -jar getdown.jar app_dir [app_id]");
                System.exit(-1);
            }
            adarg = args[aidx++];
        }

        // look for a specific app identifier
        String appId = null;
        if (args.length > aidx) {
            appId = args[aidx++];
        }

        // ensure a valid directory was supplied
        File appDir = new File(adarg);
        if (!appDir.exists() || !appDir.isDirectory()) {
            log.warning("Invalid app_dir '" + adarg + "'.");
            System.exit(-1);
        }

        // pipe our output into a file in the application directory
        if (System.getProperty("no_log_redir") == null) {
            File logFile = new File(appDir, "launcher.log");
            try {
                PrintStream logOut = new PrintStream(
                    new BufferedOutputStream(new FileOutputStream(logFile)), true);
                System.setOut(logOut);
                System.setErr(logOut);
            } catch (IOException ioe) {
                log.warning("Unable to redirect output to '" + logFile + "': " + ioe);
            }
        }

        // record a few things for posterity
        log.info("------------------ VM Info ------------------");
        log.info("-- OS Name: " + System.getProperty("os.name"));
        log.info("-- OS Arch: " + System.getProperty("os.arch"));
        log.info("-- OS Vers: " + System.getProperty("os.version"));
        log.info("-- Java Vers: " + System.getProperty("java.version"));
        log.info("-- Java Home: " + System.getProperty("java.home"));
        log.info("-- User Name: " + System.getProperty("user.name"));
        log.info("-- User Home: " + System.getProperty("user.home"));
        log.info("-- Cur dir: " + System.getProperty("user.dir"));
        log.info("---------------------------------------------");

        try {
            Getdown app = new Getdown(appDir, appId) {
                protected Container createContainer () {
                    // create our user interface, and display it
                    String title =
                        StringUtil.isBlank(_ifc.name) ? "" : _ifc.name;
                    if (_frame == null) {
                        _frame = new JFrame(title);
                        _frame.addWindowListener(new WindowAdapter() {
                            public void windowClosing (WindowEvent evt) {
                                handleWindowClose();
                            }
                        });
                        _frame.setResizable(false);
                    } else {
                        _frame.setTitle(title);
                        _frame.getContentPane().removeAll();
                    }
                    _frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
                    return _frame.getContentPane();
                }
                protected void showContainer () {
                    if (_frame != null) {
                        _frame.pack();
                        SwingUtil.centerWindow(_frame);
                        _frame.setVisible(true);
                    }
                }
                protected void disposeContainer () {
                    if (_frame != null) {
                        _frame.dispose();
                        _frame = null;
                    }
                }
                protected void exit (int exitCode) {
                    System.exit(exitCode);
                }
                protected JFrame _frame;
            };
            app.start();

        } catch (Exception e) {
            log.warning("main() failed.", e);
        }
    }
}
