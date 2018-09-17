//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2016 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.launcher;

import java.awt.EventQueue;
import java.awt.Color;
import java.awt.Container;
import java.awt.IllegalComponentStateException;
import java.awt.Image;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.WindowConstants;

import com.samskivert.swing.util.SwingUtil;
import com.threerings.getdown.data.Digest;
import com.threerings.getdown.data.EnvConfig;
import com.threerings.getdown.data.SysProps;
import com.threerings.getdown.util.LaunchUtil;
import com.threerings.getdown.util.StringUtil;
import static com.threerings.getdown.Log.log;

/**
 * The main application entry point for Getdown.
 */
public class GetdownApp
{
    /**
     * The main entry point of the Getdown launcher application.
     */
    public static void main (String[] argv) {
        try {
            start(argv);
        } catch (Exception e) {
            log.warning("main() failed.", e);
        }
    }

    /**
     * Runs Getdown as an application, using the arguments supplie as {@code argv}.
     * @return the {@code Getdown} instance that is running. {@link Getdown#start} will have been
     * called on it.
     * @throws Exception if anything goes wrong starting Getdown.
     */
    public static Getdown start (String[] argv) throws Exception {
        List<EnvConfig.Note> notes = new ArrayList<>();
        EnvConfig envc = EnvConfig.create(argv, notes);
        if (envc == null) {
            if (!notes.isEmpty()) for (EnvConfig.Note n : notes) System.err.println(n.message);
            else System.err.println("Usage: java -jar getdown.jar [app_dir] [app_id] [app args]");
            System.exit(-1);
        }

        // pipe our output into a file in the application directory
        if (!SysProps.noLogRedir()) {
            File logFile = new File(envc.appDir, "launcher.log");
            try {
                PrintStream logOut = new PrintStream(
                    new BufferedOutputStream(new FileOutputStream(logFile)), true);
                System.setOut(logOut);
                System.setErr(logOut);
            } catch (IOException ioe) {
                log.warning("Unable to redirect output to '" + logFile + "': " + ioe);
            }
        }

        // report any notes from reading our env config, and abort if necessary
        boolean abort = false;
        for (EnvConfig.Note note : notes) {
            switch (note.level) {
            case INFO: log.info(note.message); break;
            case WARN: log.warning(note.message); break;
            case ERROR: log.error(note.message); abort = true; break;
            }
        }
        if (abort) System.exit(-1);

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

        Getdown app = new Getdown(envc) {
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
                    _frame.setUndecorated(_ifc.hideDecorations);
                    try {
                        _frame.setBackground(new Color(_ifc.background, true));
                    } catch (UnsupportedOperationException e) {
                        log.warning("Failed to set background", e);
                    } catch (IllegalComponentStateException e) {
                        log.warning("Failed to set background", e);
                    }
                    _frame.setResizable(false);
                } else {
                    _frame.setTitle(title);
                    _frame.getContentPane().removeAll();
                }

                if (_ifc.iconImages != null) {
                    ArrayList<Image> icons = new ArrayList<>();
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
                        _frame.setIconImages(icons);
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
            protected void showDocument (String url) {
                if (!StringUtil.couldBeValidUrl(url)) {
                    // command injection would be possible if we allowed e.g. spaces and double quotes
                    log.warning("Invalid document URL.", "url", url);
                    return;
                }
                String[] cmdarray;
                if (LaunchUtil.isWindows()) {
                    String osName = System.getProperty("os.name", "");
                    if (osName.indexOf("9") != -1 || osName.indexOf("Me") != -1) {
                        cmdarray = new String[] {
                            "command.com", "/c", "start", "\"" + url + "\"" };
                    } else {
                        cmdarray = new String[] {
                            "cmd.exe", "/c", "start", "\"\"", "\"" + url + "\"" };
                    }
                } else if (LaunchUtil.isMacOS()) {
                    cmdarray = new String[] { "open", url };
                } else { // Linux, Solaris, etc.
                    cmdarray = new String[] { "firefox", url };
                }
                try {
                    Runtime.getRuntime().exec(cmdarray);
                } catch (Exception e) {
                    log.warning("Failed to open browser.", "cmdarray", cmdarray, e);
                }
            }

            @Override
            protected void exit (int exitCode) {
                // if we're running the app in the same JVM, don't call System.exit, but do
                // make double sure that the download window is closed.
                if (invokeDirect()) {
                    disposeContainer();
                } else {
                    System.exit(exitCode);
                }
            }

            @Override
            protected void fail (String message) {
                super.fail(message);
                // super.fail causes the UI to be created (if needed) on the next UI tick, so we
                // want to wait until that happens before we attempt to redecorate the window
                EventQueue.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        // if the frame was set to be undecorated, make window decoration available
                        // to allow the user to close the window
                        if (_frame != null && _frame.isUndecorated()) {
                            _frame.dispose();
                            Color bg = _frame.getBackground();
                            if (bg != null && bg.getAlpha() < 255) {
                                // decorated windows do not allow alpha backgrounds
                                _frame.setBackground(
                                    new Color(bg.getRed(), bg.getGreen(), bg.getBlue()));
                            }
                            _frame.setUndecorated(false);
                            showContainer();
                        }
                    }
                });
            }

            protected JFrame _frame;
        };
        app.start();
        return app;
    }
}
