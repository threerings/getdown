//
// $Id$

package com.threerings.getdown.launcher;

import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;

import javax.imageio.ImageIO;
import javax.swing.JFrame;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;

import java.net.HttpURLConnection;
import java.net.URL;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ResourceBundle;

import ca.beq.util.win32.registry.RegistryKey;
import ca.beq.util.win32.registry.RegistryValue;
import ca.beq.util.win32.registry.RootKey;

import com.samskivert.swing.util.SwingUtil;
import com.samskivert.text.MessageUtil;
import com.samskivert.util.RunAnywhere;
import com.samskivert.util.StringUtil;

import com.threerings.getdown.Log;
import com.threerings.getdown.data.Application;
import com.threerings.getdown.data.Resource;
import com.threerings.getdown.tools.Patcher;
import com.threerings.getdown.util.ConfigUtil;
import com.threerings.getdown.util.LaunchUtil;
import com.threerings.getdown.util.ProgressObserver;

/**
 * Manages the main control for the Getdown application updater and
 * deployment system.
 */
public class Getdown extends Thread
    implements Application.StatusDisplay
{
    public Getdown (File appDir, String appId)
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
        _app = new Application(appDir, appId);
        _startup = System.currentTimeMillis();
    }

    public void run ()
    {
        // if we have no messages, just bail because we're hosed; the
        // error message will be displayed to the user already
        if (_msgs == null) {
            return;
        }

        // determine whether or not we can write to our install directory
        File instdir = _app.getLocalPath("");
        if (!instdir.canWrite()) {
            String path = instdir.getPath();
            if (path.equals(".")) {
                path = System.getProperty("user.dir");
            }
            updateStatus(MessageUtil.tcompose("m.readonly_error", path));
            _dead = true;
            return;
        }

        try {
            _dead = false;
            if (detectProxy()) {
                getdown();
            } else {
                // create a panel they can use to configure the proxy settings
                _frame.getContentPane().removeAll();
                _frame.getContentPane().add(
                    new ProxyPanel(this, _msgs), BorderLayout.CENTER);
                _frame.pack();
                SwingUtil.centerWindow(_frame);
                _frame.setVisible(true);
                // allow them to close the window to abort the proxy
                // configuration
                _dead = true;
            }

        } catch (Exception e) {
            Log.logStackTrace(e);
            String msg = e.getMessage();
            if (msg == null) {
                msg = MessageUtil.compose("m.unknown_error", _ifc.installError);
            } else if (!msg.startsWith("m.")) {
                // try to do something sensible based on the type of error
                if (e instanceof FileNotFoundException) {
                    msg = MessageUtil.compose("m.missing_resource",
                        MessageUtil.taint(msg), _ifc.installError);
                } else {
                    msg = MessageUtil.compose("m.init_error",
                        MessageUtil.taint(msg), _ifc.installError);
                }
            }
            updateStatus(msg);
            _dead = true;
        }
    }

    /**
     * Configures our proxy settings (called by {@link ProxyPanel}) and
     * fires up the launcher.
     */
    public void configureProxy (String host, String port)
    {
        Log.info("User configured proxy [host=" + host +
                 ", port=" + port + "].");

        // if we're provided with valid values, create a proxy.txt file
        if (!StringUtil.isBlank(host)) {
            File pfile = _app.getLocalPath("proxy.txt");
            try {
                PrintStream pout = new PrintStream(new FileOutputStream(pfile));
                pout.println("host = " + host);
                if (!StringUtil.isBlank(port)) {
                    pout.println("port = " + port);
                }
                pout.close();
            } catch (IOException ioe) {
                Log.warning("Error creating proxy file '" + pfile +
                            "': " + ioe);
            }

            // also configure them in the JVM
            setProxyProperties(host, port);
        }

        // clear out our UI
        _frame.dispose();
        _status = null;
        _frame = null;

        // fire up a new thread
        new Thread(this).start();
    }

    /**
     * Reads and/or autodetects our proxy settings.
     *
     * @return true if we should proceed with running the launcher, false
     * if we need to wait for the user to enter proxy settings.
     */
    protected boolean detectProxy ()
    {
        // we may already have a proxy configured
        if (System.getProperty("http.proxyHost") != null) {
            return true;
        }

        // look in the Vinders registry
        if (RunAnywhere.isWindows()) {
            try {
                String host = null, port = null;
                boolean enabled = false;
                RegistryKey.initialize();
                RegistryKey r = new RegistryKey(
                    RootKey.HKEY_CURRENT_USER, PROXY_REGISTRY);
                for (Iterator iter = r.values(); iter.hasNext(); ) {
                    RegistryValue value = (RegistryValue)iter.next();
                    if (value.getName().equals("ProxyEnable")) {
                        enabled = value.getStringValue().equals("1");
                    }
                    if (value.getName().equals("ProxyServer")) {
                        String strval = value.getStringValue();
                        int cidx = strval.indexOf(":");
                        if (cidx != -1) {
                            port = strval.substring(cidx+1);
                            strval = strval.substring(0, cidx);
                        }
                        host = strval;
                    }
                }

                if (enabled) {
                    setProxyProperties(host, port);
                    return true;
                } else {
                    Log.info("Detected no proxy settings in the registry.");
                }

            } catch (Throwable t) {
                Log.info("Failed to find proxy settings in Windows registry " +
                         "[error=" + t + "].");
            }
        }

        // otherwise look for and read our proxy.txt file
        File pfile = _app.getLocalPath("proxy.txt");
        if (pfile.exists()) {
            try {
                HashMap pconf = ConfigUtil.parseConfig(pfile, false);
                setProxyProperties((String)pconf.get("host"),
                                   (String)pconf.get("port"));
                return true;

            } catch (IOException ioe) {
                Log.warning("Failed to read '" + pfile + "': " + ioe);
            }
        }

        // otherwise see if we actually need a proxy; first we have to
        // initialize our application to get some sort of interface
        // configuration and the appbase URL
        Log.info("Checking whether we need to use a proxy...");
        try {
            _ifc = _app.init(true);
        } catch (IOException ioe) {
            // no worries
        }
        updateStatus("m.detecting_proxy");

        URL rurl = _app.getConfigResource().getRemote();
        try {
            // try to make a HEAD request for this URL
            HttpURLConnection ucon = (HttpURLConnection)
                rurl.openConnection();
            ucon.setRequestMethod("HEAD");
            ucon.connect();

            // make sure we got a satisfactory response code
            if (ucon.getResponseCode() != HttpURLConnection.HTTP_OK) {
                Log.warning("Got a non-200 response but assuming we're OK " +
                            "because we got something... [url=" + rurl +
                            ", rsp=" + ucon.getResponseCode() + "].");
            }

            // we got through, so we appear not to require a proxy; make a
            // blank proxy config and get on gettin' down
            Log.info("No proxy appears to be needed.");
            try {
                pfile.createNewFile();
            } catch (IOException ioe) {
                Log.warning("Failed to create blank proxy file '" +
                            pfile + "': " + ioe);
            }
            return true;

        } catch (IOException ioe) {
            Log.info("Failed to HEAD " + rurl + ": " + ioe);
            Log.info("We probably need a proxy. Attempting to auto-detect...");
        }

        // let the caller know that we need a proxy but can't detect it
        return false;
    }

    /**
     * Configures the JVM proxy system properties.
     */
    protected void setProxyProperties (String host, String port)
    {
        if (!StringUtil.isBlank(host)) {
            System.setProperty("http.proxyHost", host);
            if (!StringUtil.isBlank(port)) {
                System.setProperty("http.proxyPort", port);
            }
            Log.info("Using proxy [host=" + host + ", port=" + port + "].");
        }
    }

    /**
     * Does the actual application validation, update and launching
     * business.
     */
    protected void getdown ()
    {
        Log.info("---------------- Proxy Info -----------------");
        Log.info("-- Proxy Host: " + System.getProperty("http.proxyHost"));
        Log.info("-- Proxy Port: " + System.getProperty("http.proxyPort"));
        Log.info("---------------------------------------------");

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
                msg = MessageUtil.compose("m.unknown_error", _ifc.installError);
            } else if (!msg.startsWith("m.")) {
                // try to do something sensible based on the type of error
                if (e instanceof FileNotFoundException) {
                    msg = MessageUtil.compose("m.missing_resource",
                        MessageUtil.taint(msg), _ifc.installError);
                } else {
                    msg = MessageUtil.compose("m.init_error", msg,
                        MessageUtil.taint(msg), _ifc.installError);
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
            ArrayList<Resource> list = new ArrayList<Resource>();
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

            // on Windows 98 and ME we need to stick around and read the
            // output of stderr lest the process fill its output buffer
            // and choke, yay!
            if (LaunchUtil.mustMonitorChildren()) {
                // close our window if it's around
                if (_frame != null) {
                    _frame.dispose();
                    _status = null;
                    _frame = null;
                }
                Log.info("Sticking around to read stderr...");
                InputStream stderr = proc.getErrorStream();
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stderr));
                while (reader.readLine() != null) {
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

        // load up our background and progress bar images
        BufferedImage bgimg = loadImage(_ifc.backgroundImage);
        BufferedImage barimg = loadImage(_ifc.progressImage);

        // create our user interface, and display it
        String title = StringUtil.isBlank(_ifc.name) ? "" : _ifc.name;
        if (_frame == null) {
            _frame = new JFrame(title);
            _frame.addWindowListener(new WindowAdapter() {
                public void windowClosing (WindowEvent evt) {
                    if (_dead) {
                        System.exit(0);
                    } else {
                        if (_abort == null) {
                            _abort = new AbortPanel(Getdown.this, _msgs);
                        }
                        _abort.pack();
                        SwingUtil.centerWindow(_abort);
                        _abort.setVisible(true);
                        _abort.setState(JFrame.NORMAL);
                        _abort.requestFocus();
                    }
                }
            });
            _frame.setResizable(false);

        } else {
            _frame.setTitle(title);
            _frame.getContentPane().removeAll();
        }
        _frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        _status = new StatusPanel(_msgs, _ifc, bgimg, barimg);
        _frame.getContentPane().add(_status, BorderLayout.CENTER);
        _frame.pack();
        SwingUtil.centerWindow(_frame);
        _frame.setVisible(true);
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

    protected BufferedImage loadImage (String path)
    {
        if (StringUtil.isBlank(path)) {
            return null;
        }
        File imgpath = _app.getLocalPath(path);
        try {
            return ImageIO.read(imgpath);
        } catch (IOException ioe) {
            Log.warning("Failed to load image [path=" + imgpath +
                        ", error=" + ioe + "].");
            return null;
        }
    }

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
            Log.warning("Invalid app_dir '" + adarg + "'.");
            System.exit(-1);
        }

        // pipe our output into a file in the application directory
        if (System.getProperty("no_log_redir") == null) {
            File log = new File(appDir, "launcher.log");
            try {
                PrintStream logOut = new PrintStream(
                    new BufferedOutputStream(new FileOutputStream(log)), true);
                System.setOut(logOut);
                System.setErr(logOut);
            } catch (IOException ioe) {
                Log.warning("Unable to redirect output to '" + log +
                            "': " + ioe);
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
        Log.info("---------------------------------------------");

        try {
            Getdown app = new Getdown(appDir, appId);
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
    protected AbortPanel _abort;

    protected boolean _dead;
    protected long _startup;

    protected static final int MAX_LOOPS = 5;
    protected static final long MIN_EXIST_TIME = 5000L;
    protected static final String PROXY_REGISTRY =
        "Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings";
}
