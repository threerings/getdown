//
// $Id: Getdown.java,v 1.5 2004/07/07 08:42:40 mdb Exp $

package com.threerings.getdown.launcher;

import java.awt.BorderLayout;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import javax.imageio.ImageIO;
import javax.swing.JFrame;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import java.util.List;

import org.apache.commons.io.TeeOutputStream;

import com.samskivert.swing.util.SwingUtil;
import com.samskivert.util.StringUtil;

import com.threerings.getdown.Log;
import com.threerings.getdown.data.Application;
import com.threerings.getdown.data.Resource;

/**
 * Manages the main control for the Getdown application updater and
 * deployment system.
 */
public class Getdown
{
    public Getdown (File appDir)
    {
        _app = new Application(appDir);
    }

    public void run ()
    {
        try {
            for (int ii = 0; ii < MAX_LOOPS; ii++) {
                _ifc = _app.init();

                if (_app.verifyMetadata()) {
                    Log.info("Application requires update.");
                    update();

                } else {
                    Log.info("Metadata verified.");
                    List failures = _app.verifyResources();
                    if (failures == null) {
                        Log.info("Resources verified.");
                        launch();
                        return;

                    } else {
                        Log.info(failures.size() + " rsrcs require update.");
                        download(failures);
                        // now we'll loop back and try it all again
                    }
                }
            }
            Log.warning("Pants! We couldn't get the job done.");

        } catch (Exception e) {
            Log.logStackTrace(e);
        }
    }

    /**
     * Called if the application is determined to be of an old version.
     */
    protected void update ()
    {
    }

    /**
     * Called if the application is determined to require resource
     * downloads.
     */
    protected void download (List resources)
    {
        final Object lock = new Object();

        // create our user interface
        createInterface();

        // create a downloader to download our resources
        Downloader.Observer obs = new Downloader.Observer() {
            public void resolvingDownloads () {
                Log.info("Resolving downloads...");
                _status.setStatus("Resolving downloads...");
            }

            public void downloadProgress (int percent, long remaining) {
                _status.setStatus("Download progress " + percent + "%, " +
                                  remaining + " seconds remaining.");
                _status.setProgress(percent);
                if (percent == 100) {
                    synchronized (lock) {
                        lock.notify();
                    }
                }
            }

            public void downloadFailed (Resource rsrc, Exception e) {
                _status.setStatus("Download failed: " + e.getMessage());
                Log.warning("Download failed [rsrc=" + rsrc + "].");
                Log.logStackTrace(e);
                synchronized (lock) {
                    lock.notify();
                }
            }
        };
        Downloader dl = new Downloader(resources, obs);
        dl.start();

        // now wait for it to complete
        synchronized (lock) {
            try {
                lock.wait();
            } catch (InterruptedException ie) {
                Log.warning("Waitus interruptus " + ie + ".");
            }
        }
    }

    /**
     * Called to launch the application if everything is determined to be
     * ready to go.
     */
    protected void launch ()
    {
        if (_status != null) {
            _status.setStatus("Launching...");
        }

        try {
            Process proc = _app.createProcess();
            System.exit(0);
        } catch (IOException ioe) {
            Log.logStackTrace(ioe);
        }
    }

    /**
     * Creates our user interface, which we avoid doing unless we actually
     * have to update something.
     */
    protected void createInterface ()
    {
        if (_frame != null) {
            return;
        }

        Rectangle ppos = (_ifc.progress == null) ? DEFAULT_PPOS : _ifc.progress;
        Rectangle spos = (_ifc.status == null) ? DEFAULT_STATUS : _ifc.status;
        Rectangle bounds = ppos.union(spos);
        bounds.grow(5, 5);

        // if we have a background image, load it up
        BufferedImage bgimg = null;
        if (!StringUtil.blank(_ifc.background)) {
            File bgpath = _app.getLocalPath(_ifc.background);
            try {
                bgimg = ImageIO.read(bgpath);
                bounds.setRect(0, 0, bgimg.getWidth(), bgimg.getHeight());
            } catch (IOException ioe) {
                Log.warning("Failed to read UI background [path=" + bgpath +
                            ", error=" + ioe + "].");
            }
        }

        // create our user interface, and display it
        _frame = new JFrame(StringUtil.blank(_ifc.name) ? "" : _ifc.name);
        _status = new StatusPanel(bounds, bgimg, ppos, spos);
        _frame.getContentPane().add(_status, BorderLayout.CENTER);
        _frame.pack();
        SwingUtil.centerWindow(_frame);
        _frame.show();
    }

    public static void main (String[] args)
    {
        // ensure the proper parameters are passed
        if (args.length != 1) {
            System.err.println("Usage: java -jar getdown.jar app_dir");
            System.exit(-1);
        }

        // ensure a valid directory was supplied
        File appDir = new File(args[0]);
        if (!appDir.exists() || !appDir.isDirectory()) {
            Log.warning("Invalid app_dir '" + args[0] + "'.");
            System.exit(-1);
        }

//         // tee our output into a file in the application directory
//         File log = new File(appDir, "getdown.log");
//         try {
//             FileOutputStream fout = new FileOutputStream(log);
//             System.setOut(new PrintStream(
//                               new TeeOutputStream(System.out, fout)));
//             System.setErr(new PrintStream(
//                               new TeeOutputStream(System.err, fout)));
//         } catch (IOException ioe) {
//             Log.warning("Unable to redirect output to '" + log + "': " + ioe);
//         }

        try {
            Getdown app = new Getdown(appDir);
            app.run();
        } catch (Exception e) {
            Log.logStackTrace(e);
        }
    }

    protected Application _app;
    protected Application.UpdateInterface _ifc;

    protected JFrame _frame;
    protected StatusPanel _status;

    protected static final int MAX_LOOPS = 5;

    protected static final Rectangle DEFAULT_PPOS =
        new Rectangle(0, 0, 300, 15);
    protected static final Rectangle DEFAULT_STATUS =
        new Rectangle(0, 20, 300, 200);
}
