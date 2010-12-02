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

package com.threerings.getdown.launcher;

import java.awt.Container;
import java.awt.Image;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.WindowConstants;

import com.samskivert.util.StringUtil;

import com.samskivert.swing.util.SwingUtil;

import static com.threerings.getdown.Log.log;

/**
 * The main application entry point for Getdown.
 */
public class GetdownApp
{
    public static void main (String[] argArray)
    {
        // maybe they specified the appdir in a system property
        int aidx = 0;
        List<String> args = Arrays.asList(argArray);
        String adarg = System.getProperty("appdir");
        // if not, check for a command line argument
        if (StringUtil.isBlank(adarg)) {
            if (args.isEmpty()) {
                System.err.println("Usage: java -jar getdown.jar app_dir [app_id] [app args]");
                System.exit(-1);
            }
            adarg = args.get(aidx++);
        }

        // look for a specific app identifier
        String appId = (aidx < args.size()) ? args.get(aidx++) : null;

        // pass along anything after that as jvm args
        String[] appargs = (aidx < args.size())
            ? args.subList(aidx, args.size()).toArray(new String[0]) : null;

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
            Getdown app = new Getdown(appDir, appId, null, null, appargs) {
                @Override
                protected Container createContainer () {
                    // create our user interface, and display it
                    String title = StringUtil.isBlank(_ifc.name) ? "" : _ifc.name;
                    if (_frame == null) {
                        _frame = new JFrame(title);
                        _frame.addWindowListener(new WindowAdapter() {
                            @Override
                            public void windowClosing (WindowEvent evt) {
                                handleWindowClose();
                            }
                        });
                        _frame.setResizable(false);
                    } else {
                        _frame.setTitle(title);
                        _frame.getContentPane().removeAll();
                    }

                    if (_ifc.iconImages != null) {
                        ArrayList<Image> icons = new ArrayList<Image>();
                        for (String path : _ifc.iconImages) {
                            Image img = loadImage(path);
                            if (img == null) {
                                log.warning("Error loading icon image", "path", path);
                            } else {
                                icons.add(img);
                            }
                        }
                        if (icons.isEmpty()) {
                            log.warning("Failed to load any icons", "iconImages", _ifc.iconImages);
                        } else {
                            SwingUtil.setFrameIcons(_frame, icons);
                        }
                    }

                    _frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
                    return _frame.getContentPane();
                }
                @Override
                protected void showContainer () {
                    if (_frame != null) {
                        _frame.pack();
                        SwingUtil.centerWindow(_frame);
                        _frame.setVisible(true);
                    }
                }
                @Override
                protected void disposeContainer () {
                    if (_frame != null) {
                        _frame.dispose();
                        _frame = null;
                    }
                }
                @Override
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
