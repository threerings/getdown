//
// $Id: Getdown.java,v 1.4 2004/07/06 05:13:36 mdb Exp $

package com.threerings.getdown.launcher;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import java.util.List;

import org.apache.commons.io.TeeOutputStream;

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
                _app.init();

                if (_app.verifyMetadata()) {
                    Log.info("Application requires update.");

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
        Log.info("We're needin' an update Cap'n!");
    }

    /**
     * Called if the application is determined to require resource
     * downloads.
     */
    protected void download (List resources)
    {
        final Object lock = new Object();

        // create a downloader to download our resources
        Downloader.Observer obs = new Downloader.Observer() {
            public void resolvingDownloads () {
                Log.info("Resolving downloads...");
            }

            public void downloadProgress (int percent, long remaining) {
                Log.info("Download progress " + percent + "% " +
                         remaining + "s remaining.");
                if (percent == 100) {
                    synchronized (lock) {
                        lock.notify();
                    }
                }
            }

            public void downloadFailed (Resource rsrc, Exception e) {
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
        Log.info("All systems go!");
        try {
            Process proc = _app.createProcess();
        } catch (IOException ioe) {
            Log.logStackTrace(ioe);
        }
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

    protected static final int MAX_LOOPS = 5;
}
