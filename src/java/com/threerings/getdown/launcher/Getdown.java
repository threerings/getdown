//
// $Id: Getdown.java,v 1.27 2004/07/30 18:14:21 mdb Exp $

package com.threerings.getdown.launcher;

import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;

import javax.imageio.ImageIO;
import javax.swing.JFrame;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;

import java.net.URL;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;

// import org.apache.commons.io.TeeOutputStream;

import com.samskivert.swing.util.SwingUtil;
import com.samskivert.text.MessageUtil;
import com.samskivert.util.StringUtil;

import com.threerings.getdown.Log;
import com.threerings.getdown.data.Application;
import com.threerings.getdown.data.Resource;
import com.threerings.getdown.tools.Patcher;
import com.threerings.getdown.util.ProgressObserver;
import com.threerings.getdown.util.ProxyUtil;

/**
 * Manages the main control for the Getdown application updater and
 * deployment system.
 */
public class Getdown extends Thread
    implements Application.StatusDisplay
{
    public Getdown (File appDir)
    {
        super("Getdown");
        try {
            _msgs = ResourceBundle.getBundle("com.threerings.getdown.messages");
        } catch (Exception e) {
            // welcome to hell, where java can't cope with a classpath
            // that contains jars that live in a directory that contains a
            // !, at least the same bug happens on all platforms
            String dir = appDir.toString();
            if (dir.equals(".")) {
                dir = System.getProperty("user.dir");
            }
            String errmsg = "The directory in which this application is " +
                "installed:\n" + dir + "\nis invalid. The directory " +
                "must not contain the '!' character. Please reinstall.";
            updateStatus(errmsg);
            _dead = true;
        }
        _app = new Application(appDir);
        _startup = System.currentTimeMillis();
    }

    public void run ()
    {
        // if we have no messages, just bail because we're hosed; the
        // error message will be displayed to the user already
        if (_msgs == null) {
            return;
        }

        try {
            // first parses our application deployment file
            try {
                _ifc = _app.init(true);
            } catch (IOException ioe) {
                Log.warning("Failed to parse 'getdown.txt': " + ioe);
                _app.attemptRecovery(this);
                // and re-initalize
                _ifc = _app.init(true);
                // now force our UI to be recreated with the updated info
                createInterface(true);
            }

            for (int ii = 0; ii < MAX_LOOPS; ii++) {
                // make sure we have the desired version and that the
                // metadata files are valid...
                setStatus("m.validating", -1, -1L, false);
                if (_app.verifyMetadata(this)) {
                    Log.info("Application requires update.");
                    update();
                    // loop back again and reverify the metadata
                    continue;
                }

                // now verify our resources...
                setStatus("m.validating", -1, -1L, false);
                List failures = _app.verifyResources(_progobs);
                if (failures == null) {
                    Log.info("Resources verified.");
                    launch();
                    return;
                }

                // redownload any that are corrupt or invalid...
                Log.info(failures.size() + " rsrcs require update.");
                download(failures);
                // now we'll loop back and try it all again
            }

            Log.warning("Pants! We couldn't get the job done.");
            throw new IOException("m.unable_to_repair");

        } catch (Exception e) {
            Log.logStackTrace(e);
            String msg = e.getMessage();
            if (msg == null) {
                msg = "m.unknown_error";
            } else if (!msg.startsWith("m.")) {
                // try to do something sensible based on the type of error
                if (e instanceof FileNotFoundException) {
                    msg = MessageUtil.tcompose("m.missing_resource", msg);
                } else {
                    msg = MessageUtil.tcompose("m.init_error", msg);
                }
            }
            updateStatus(msg);
            _dead = true;
        }
    }

    // documentation inherited from interface
    public void updateStatus (String message)
    {
        setStatus(message, -1, -1L, true);
    }

    /**
     * Called if the application is determined to be of an old version.
     */
    protected void update ()
        throws IOException
    {
        // first clear all validation markers
        _app.clearValidationMarkers();

        // attempt to download the patch file
        final Resource patch = _app.getPatchResource();
        if (patch != null) {
            // download the patch file...
            ArrayList list = new ArrayList();
            list.add(patch);
            download(list);

            // and apply it...
            updateStatus("m.patching");
            try {
                Patcher patcher = new Patcher();
                patcher.patch(patch.getLocal().getParentFile(),
                              patch.getLocal(), _progobs);
            } catch (Exception e) {
                Log.warning("Failed to apply patch.");
                Log.logStackTrace(e);
            }

            // lastly clean up the patch file
            if (!patch.getLocal().delete()) {
                Log.warning("Failed to delete '" + patch + "'.");
                patch.getLocal().deleteOnExit();
            }
        }
        // if the patch resource is null, that means something was booched
        // in the application, so we skip the patching process but update
        // the metadata which will result in a "brute force" upgrade

        // finally update our metadata files...
        _app.updateMetadata();
        // ...and reinitialize the application
        _ifc = _app.init(true);
    }

    /**
     * Called if the application is determined to require resource
     * downloads.
     */
    protected void download (List resources)
    {
        final Object lock = new Object();

        // create our user interface
        createInterface(false);

        // create a downloader to download our resources
        Downloader.Observer obs = new Downloader.Observer() {
            public void resolvingDownloads () {
                updateStatus("m.resolving");
            }

            public void downloadProgress (int percent, long remaining) {
                setStatus("m.downloading", percent, remaining, true);
                if (percent == 100) {
                    synchronized (lock) {
                        lock.notify();
                    }
                }
            }

            public void downloadFailed (Resource rsrc, Exception e) {
                updateStatus(
                    MessageUtil.tcompose("m.failure", e.getMessage()));
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
        setStatus("m.launching", 100, -1L, false);

        try {
            Process proc = _app.createProcess();

            // on Windows 98 we need to stick around and read the output
            // of stdout lest the process fills its output buffer and
            // chokes, yay!
            if (System.getProperty("os.name").indexOf("Windows 98") != -1) {
                Log.info("Sticking around to read stderr on Win98...");
                InputStream stderr = proc.getErrorStream();
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stderr));
                String line = null;
                while ((line = reader.readLine()) != null) {
                    // nothing doing!
                }
                Log.info("Process exited: " + proc.waitFor());
            }

            // if we have a UI open and we haven't been around for at
            // least 5 seconds, don't stick a fork in ourselves straight
            // away but give our lovely user a chance to see what we're
            // doing
            long uptime = System.currentTimeMillis() - _startup;
            if (_frame != null && uptime < MIN_EXIST_TIME) {
                try {
                    Thread.sleep(MIN_EXIST_TIME - uptime);
                } catch (Exception e) {
                }
            }
            System.exit(0);

        } catch (Exception e) {
            Log.logStackTrace(e);
        }
    }

    /**
     * Creates our user interface, which we avoid doing unless we actually
     * have to update something.
     */
    protected void createInterface (boolean force)
    {
        if (_frame != null && !force) {
            return;
        }

        // if we have a background image, load it up
        BufferedImage bgimg = null;
        if (!StringUtil.blank(_ifc.background)) {
            File bgpath = _app.getLocalPath(_ifc.background);
            try {
                bgimg = ImageIO.read(bgpath);
            } catch (IOException ioe) {
                Log.warning("Failed to read UI background [path=" + bgpath +
                            ", error=" + ioe + "].");
            }
        }

        // create our user interface, and display it
        String title = StringUtil.blank(_ifc.name) ? "" : _ifc.name;
        if (_frame == null) {
            _frame = new JFrame(title);
            _frame.addWindowListener(new WindowAdapter() {
                public void windowClosing (WindowEvent evt) {
                    if (_dead || _frame.getState() == JFrame.ICONIFIED) {
                        System.exit(0);
                    } else {
                        _frame.setState(JFrame.ICONIFIED);
                    }
                }
            });

        } else {
            _frame.setTitle(title);
            _frame.getContentPane().removeAll();
        }
        _frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        _status = new StatusPanel(_msgs, _ifc, bgimg);
        _frame.getContentPane().add(_status, BorderLayout.CENTER);
        _frame.pack();
        SwingUtil.centerWindow(_frame);
        _frame.show();
    }

    protected void setStatus (final String message, final int percent,
                              final long remaining, boolean createUI)
    {
        if (_status == null && createUI) {
            createInterface(false);
        }

        if (_status != null) {
            EventQueue.invokeLater(new Runnable() {
                public void run () {
                    if (message != null) {
                        _status.setStatus(message);
                    }
                    if (percent >= 0) {
                        _status.setProgress(percent, remaining);
                    }
                }
            });
        }
    }

    public static void main (String[] args)
    {
        // maybe they specified the appdir in a system property
        String adarg = System.getProperty("appdir");
        // if not, check for a command line argument
        if (StringUtil.blank(adarg)) {
            if (args.length != 1) {
                System.err.println("Usage: java -jar getdown.jar app_dir");
                System.exit(-1);
            }
            adarg = args[0];
        }

        // ensure a valid directory was supplied
        File appDir = new File(adarg);
        if (!appDir.exists() || !appDir.isDirectory()) {
            Log.warning("Invalid app_dir '" + adarg + "'.");
            System.exit(-1);
        }

        // pipe our output into a file in the application directory
        File log = new File(appDir, "launcher.log");
        try {
            FileOutputStream fout = new FileOutputStream(log);
            System.setOut(new PrintStream(fout));
            System.setErr(new PrintStream(fout));
        } catch (IOException ioe) {
            Log.warning("Unable to redirect output to '" + log + "': " + ioe);
        }

        // attempt to obtain our proxy information; it may be specified
        // via system properties or we can autodetect it
        if (System.getProperty("http.proxyHost") == null) {
            try {
                URL sample = new URL("http://www.threerings.net/");
                if (ProxyUtil.detectProxy(sample)) {
                    String host = ProxyUtil.getProxyIP();
                    String port = ProxyUtil.getProxyPort();
                    ProxyUtil.configureProxy(host, port);
                }
            } catch (Exception e) {
                Log.warning("Failed to detect proxy: " + e);
            }
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
        Log.info("-- Proxy Host: " + System.getProperty("http.proxyHost"));
        Log.info("-- Proxy Port: " + System.getProperty("http.proxyPort"));
        Log.info("---------------------------------------------");

        try {
            Getdown app = new Getdown(appDir);
            app.start();
        } catch (Exception e) {
            Log.logStackTrace(e);
        }
    }

    /** Used to pass progress on to our user interface. */
    protected ProgressObserver _progobs = new ProgressObserver() {
        public void progress (final int percent) {
            setStatus(null, percent, -1L, false);
        }
    };

    protected Application _app;
    protected Application.UpdateInterface _ifc =
        new Application.UpdateInterface();

    protected ResourceBundle _msgs;
    protected JFrame _frame;
    protected StatusPanel _status;

    protected boolean _dead;
    protected long _startup;

    protected static final int MAX_LOOPS = 5;
    protected static final long MIN_EXIST_TIME = 5000L;
}
