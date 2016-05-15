//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2016 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.launcher;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import javax.swing.JApplet;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLayeredPane;

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
import java.net.URLConnection;

import java.security.cert.Certificate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import ca.beq.util.win32.registry.RegistryKey;
import ca.beq.util.win32.registry.RegistryValue;
import ca.beq.util.win32.registry.RootKey;

import com.samskivert.swing.util.SwingUtil;
import com.samskivert.text.MessageUtil;
import com.samskivert.util.RunAnywhere;
import com.samskivert.util.StringUtil;

import com.threerings.getdown.data.Application.UpdateInterface.Step;
import com.threerings.getdown.data.Application;
import com.threerings.getdown.data.Resource;
import com.threerings.getdown.data.SysProps;
import com.threerings.getdown.net.Downloader;
import com.threerings.getdown.net.HTTPDownloader;
import com.threerings.getdown.tools.Patcher;
import com.threerings.getdown.util.ConfigUtil;
import com.threerings.getdown.util.ConnectionUtil;
import com.threerings.getdown.util.LaunchUtil;
import com.threerings.getdown.util.ProgressAggregator;
import com.threerings.getdown.util.ProgressObserver;
import com.threerings.getdown.util.VersionUtil;

import static com.threerings.getdown.Log.log;

/**
 * Manages the main control for the Getdown application updater and deployment system.
 */
public abstract class Getdown extends Thread
    implements Application.StatusDisplay, ImageLoader
{
    public static void main (String[] args)
    {
        // legacy support
        GetdownApp.main(args);
    }

    public Getdown (File appDir, String appId)
    {
        this(appDir, appId, null, null, null);
    }

    public Getdown (File appDir, String appId, List<Certificate> signers,
                    String[] jvmargs, String[] appargs)
    {
        super("Getdown");
        try {
            // If the silent property exists, install without bringing up any gui. If it equals
            // launch, start the application after installing. Otherwise, just install and exit.
            _silent = SysProps.silent();
            if (_silent) {
                _launchInSilent = SysProps.launchInSilent();
            }
            _delay = SysProps.startDelay();
        } catch (SecurityException se) {
            // don't freak out, just assume non-silent and no delay; we're probably already
            // recovering from a security failure
        }
        try {
            _msgs = ResourceBundle.getBundle("com.threerings.getdown.messages");
        } catch (Exception e) {
            // welcome to hell, where java can't cope with a classpath that contains jars that live
            // in a directory that contains a !, at least the same bug happens on all platforms
            String dir = appDir.toString();
            if (dir.equals(".")) {
                dir = System.getProperty("user.dir");
            }
            String errmsg = "The directory in which this application is installed:\n" + dir +
                "\nis invalid (" + e.getMessage() + "). If the full path to the app directory " +
                "contains the '!' character, this will trigger this error.";
            fail(errmsg);
        }
        _app = new Application(appDir, appId, signers, jvmargs, appargs);
        _startup = System.currentTimeMillis();
    }

    /**
     * This is used by the applet which always needs a user interface and wants to load it as soon
     * as possible.
     */
    public void preInit ()
    {
        try {
            _ifc = _app.init(true);
            createInterfaceAsync(true);
        } catch (Exception e) {
            log.warning("Failed to preinit: " + e);
            createInterfaceAsync(true);
        }
    }

    @Override
    public void run ()
    {
        // if we have no messages, just bail because we're hosed; the error message will be
        // displayed to the user already
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
            fail(MessageUtil.tcompose("m.readonly_error", path));
            return;
        }

        try {
            _dead = false;
            // if we fail to detect a proxy, but we're allowed to run offline, then go ahead and
            // run the app anyway because we're prepared to cope with not being able to update
            if (detectProxy() || _app.allowOffline()) {
                getdown();
            } else if (_silent) {
                log.warning("Need a proxy, but we don't want to bother anyone.  Exiting.");
            } else {
                // create a panel they can use to configure the proxy settings
                _container = createContainer();
                _container.add(new ProxyPanel(this, _msgs), BorderLayout.CENTER);
                showContainer();
                // allow them to close the window to abort the proxy configuration
                _dead = true;
            }

        } catch (Exception e) {
            log.warning("run() failed.", e);
            String msg = e.getMessage();
            if (msg == null) {
                msg = MessageUtil.compose("m.unknown_error", _ifc.installError);
            } else if (!msg.startsWith("m.")) {
                // try to do something sensible based on the type of error
                if (e instanceof FileNotFoundException) {
                    msg = MessageUtil.compose(
                        "m.missing_resource", MessageUtil.taint(msg), _ifc.installError);
                } else {
                    msg = MessageUtil.compose(
                        "m.init_error", MessageUtil.taint(msg), _ifc.installError);
                }
            }
            fail(msg);
        }
    }

    /**
     * Configures our proxy settings (called by {@link ProxyPanel}) and fires up the launcher.
     */
    public void configureProxy (String host, String port)
    {
        log.info("User configured proxy", "host", host, "port", port);

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
                log.warning("Error creating proxy file '" + pfile + "': " + ioe);
            }

            // also configure them in the JVM
            setProxyProperties(host, port);
        }

        // clear out our UI
        disposeContainer();
        _container = null;

        // fire up a new thread
        new Thread(this).start();
    }

    /**
     * Reads and/or autodetects our proxy settings.
     *
     * @return true if we should proceed with running the launcher, false if we need to wait for
     * the user to enter proxy settings.
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
                RegistryKey r = new RegistryKey(RootKey.HKEY_CURRENT_USER, PROXY_REGISTRY);
                for (Iterator<?> iter = r.values(); iter.hasNext(); ) {
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
                    log.info("Detected no proxy settings in the registry.");
                }

            } catch (Throwable t) {
                log.info("Failed to find proxy settings in Windows registry", "error", t);
            }
        }

        // otherwise look for and read our proxy.txt file
        File pfile = _app.getLocalPath("proxy.txt");
        if (pfile.exists()) {
            try {
                Map<String, Object> pconf = ConfigUtil.parseConfig(pfile, false);
                setProxyProperties((String)pconf.get("host"), (String)pconf.get("port"));
                return true;
            } catch (IOException ioe) {
                log.warning("Failed to read '" + pfile + "': " + ioe);
            }
        }

        // otherwise see if we actually need a proxy; first we have to initialize our application
        // to get some sort of interface configuration and the appbase URL
        log.info("Checking whether we need to use a proxy...");
        try {
            _ifc = _app.init(true);
        } catch (IOException ioe) {
            // no worries
        }
        updateStatus("m.detecting_proxy");

        URL rurl = _app.getConfigResource().getRemote();
        try {
            // try to make a HEAD request for this URL
            URLConnection conn = ConnectionUtil.open(rurl);
            if (conn instanceof HttpURLConnection) {
                HttpURLConnection hcon = (HttpURLConnection)conn;
                try {
                    hcon.setRequestMethod("HEAD");
                    hcon.connect();
                    // make sure we got a satisfactory response code
                    if (hcon.getResponseCode() != HttpURLConnection.HTTP_OK) {
                        log.warning("Got a non-200 response but assuming we're OK because we got " +
                                    "something...", "url", rurl, "rsp", hcon.getResponseCode());
                    }
                } finally {
                    hcon.disconnect();
                }
            }

            // we got through, so we appear not to require a proxy; make a blank proxy config and
            // get on gettin' down
            log.info("No proxy appears to be needed.");
            try {
                pfile.createNewFile();
            } catch (IOException ioe) {
                log.warning("Failed to create blank proxy file '" + pfile + "': " + ioe);
            }
            return true;

        } catch (IOException ioe) {
            log.info("Failed to HEAD " + rurl + ": " + ioe);
            log.info("We probably need a proxy, but auto-detection failed.");
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
            System.setProperty("https.proxyHost", host);
            if (!StringUtil.isBlank(port)) {
                System.setProperty("http.proxyPort", port);
                System.setProperty("https.proxyPort", port);
            }
            log.info("Using proxy", "host", host, "port", port);
        }
    }

    /**
     * Does the actual application validation, update and launching business.
     */
    protected void getdown ()
    {
        log.info("---------------- Proxy Info -----------------");
        log.info("-- Proxy Host: " + System.getProperty("http.proxyHost"));
        log.info("-- Proxy Port: " + System.getProperty("http.proxyPort"));
        log.info("---------------------------------------------");

        try {
            // first parses our application deployment file
            try {
                _ifc = _app.init(true);
            } catch (IOException ioe) {
                log.warning("Failed to initialize: " + ioe);
                _app.attemptRecovery(this);
                // and re-initalize
                _ifc = _app.init(true);
                // now force our UI to be recreated with the updated info
                createInterfaceAsync(true);
            }
            if (!_app.lockForUpdates()) {
                throw new MultipleGetdownRunning();
            }

            // Update the config modtime so a sleeping getdown will notice the change.
            File config = _app.getLocalPath(Application.CONFIG_FILE);
            if (!config.setLastModified(System.currentTimeMillis())) {
                log.warning("Unable to set modtime on config file, will be unable to check for " +
                            "another instance of getdown running while this one waits.");
            }
            if (_delay > 0) {
                // don't hold the lock while waiting, let another getdown proceed if it starts.
                _app.releaseLock();
                // Store the config modtime before waiting the delay amount of time
                long lastConfigModtime = config.lastModified();
                log.info("Waiting " + _delay + " minutes before beginning actual work.");
                Thread.sleep(_delay * 60 * 1000);
                if (lastConfigModtime < config.lastModified()) {
                    log.warning("getdown.txt was modified while getdown was waiting.");
                    throw new MultipleGetdownRunning();
                }
            }

            // we create this tracking counter here so that we properly note the first time through
            // the update process whether we previously had validated resources (which means this
            // is not a first time install); we may, in the course of updating, wipe out our
            // validation markers and revalidate which would make us think we were doing a fresh
            // install if we didn't specifically remember that we had validated resources the first
            // time through
            int[] alreadyValid = new int[1];

            // we'll keep track of all the resources we unpack
            Set<Resource> unpacked = new HashSet<Resource>();

            //setStep(Step.START);
            for (int ii = 0; ii < MAX_LOOPS; ii++) {
                // if we aren't running in a JVM that meets our version requirements, either
                // complain or attempt to download and install the appropriate version
                if (!_app.haveValidJavaVersion()) {
                    // download and install the necessary version of java, then loop back again and
                    // reverify everything; if we can't download java; we'll throw an exception
                    log.info("Attempting to update Java VM...");
                    setStep(Step.UPDATE_JAVA);
                    _enableTracking = true; // always track JVM downloads
                    try {
                        updateJava();
                    } finally {
                        _enableTracking = false;
                    }
                    continue;
                }

                // make sure we have the desired version and that the metadata files are valid...
                setStep(Step.VERIFY_METADATA);
                setStatusAsync("m.validating", -1, -1L, false);
                if (_app.verifyMetadata(this)) {
                    log.info("Application requires update.");
                    update();
                    // loop back again and reverify the metadata
                    continue;
                }

                // now verify our resources...
                setStep(Step.VERIFY_RESOURCES);
                setStatusAsync("m.validating", -1, -1L, false);
                List<Resource> failures = _app.verifyResources(_progobs, alreadyValid, unpacked);
                if (failures == null) {
                    log.info("Resources verified.");

                    // if we were downloaded in full from another service (say, Steam), we may
                    // not have unpacked all of our resources yet
                    if (Boolean.getBoolean("check_unpacked")) {
                        File ufile = _app.getLocalPath("unpacked.dat");
                        long version = -1;
                        long aversion = _app.getVersion();
                        if (!ufile.exists()) {
                            ufile.createNewFile();
                        } else {
                            version = VersionUtil.readVersion(ufile);
                        }

                        if (version < aversion) {
                            log.info("Performing unpack.",
                                    "version", version, "aversion", aversion);
                            setStep(Step.UNPACK);
                            updateStatus("m.validating");
                            _app.unpackResources(_progobs, unpacked);
                            try {
                                VersionUtil.writeVersion(ufile, aversion);
                            } catch (IOException ioe) {
                                log.warning("Failed to update unpacked version", ioe);
                            }
                        }
                    }

                    // Only launch if we aren't in silent mode. Some mystery program starting out
                    // of the blue would be disconcerting.
                    if (!_silent || _launchInSilent) {
                        if (Thread.interrupted()) {
                            // One last interrupted check so we don't launch as the applet aborts
                            throw new InterruptedException("m.applet_stopped");
                        }
                        // And another final check for the lock. It'll already be held unless
                        // we're in silent mode.
                        _app.lockForUpdates();
                        launch();
                    }
                    return;
                }

                try {
                    // if any of our resources have already been marked valid this is not a first
                    // time install and we don't want to enable tracking
                    _enableTracking = (alreadyValid[0] == 0);
                    reportTrackingEvent("app_start", -1);

                    // redownload any that are corrupt or invalid...
                    log.info(failures.size() + " of " + _app.getAllActiveResources().size() +
                             " rsrcs require update (" + alreadyValid[0] + " assumed valid).");
                    setStep(Step.REDOWNLOAD_RESOURCES);
                    download(failures);

                    reportTrackingEvent("app_complete", -1);
                } finally {
                    _enableTracking = false;
                }

                // now we'll loop back and try it all again
            }

            log.warning("Pants! We couldn't get the job done.");
            throw new IOException("m.unable_to_repair");

        } catch (Exception e) {
            log.warning("getdown() failed.", e);
            String msg = e.getMessage();
            if (msg == null) {
                msg = MessageUtil.compose("m.unknown_error", _ifc.installError);
            } else if (!msg.startsWith("m.")) {
                // try to do something sensible based on the type of error
                if (e instanceof FileNotFoundException) {
                    msg = MessageUtil.compose(
                        "m.missing_resource", MessageUtil.taint(msg), _ifc.installError);
                } else {
                    msg = MessageUtil.compose(
                        "m.init_error", MessageUtil.taint(msg), _ifc.installError);
                }
            }
            // Since we're dead, clear off the 'time remaining' label along with displaying the
            // error message
            fail(msg);
            _app.releaseLock();
        }
    }

    // documentation inherited from interface
    public void updateStatus (String message)
    {
        setStatusAsync(message, -1, -1L, true);
    }

    /**
     * Load the image at the path. Before trying the exact path/file specified we will look to see
     * if we can find a localized version by sticking a {@code _<language>} in front of the "." in
     * the filename.
     */
    public BufferedImage loadImage (String path)
    {
        if (StringUtil.isBlank(path)) {
            return null;
        }

        File imgpath = null;
        try {
            // First try for a localized image.
            String localeStr = Locale.getDefault().getLanguage();
            imgpath = _app.getLocalPath(path.replace(".", "_" + localeStr + "."));
            return ImageIO.read(imgpath);
        } catch (IOException ioe) {
            // No biggie, we'll try the generic one.
        }

        // If that didn't work, try a generic one.
        try {
            imgpath = _app.getLocalPath(path);
            return ImageIO.read(imgpath);
        } catch (IOException ioe2) {
            log.warning("Failed to load image", "path", imgpath, "error", ioe2);
            return null;
        }
    }

    /**
     * Downloads and installs an Java VM bundled with the application. This is called if we are not
     * running with the necessary Java version.
     */
    protected void updateJava ()
        throws IOException, InterruptedException
    {
        Resource vmjar = _app.getJavaVMResource();
        if (vmjar == null) {
            throw new IOException("m.java_download_failed");
        }

        reportTrackingEvent("jvm_start", -1);

        updateStatus("m.downloading_java");
        List<Resource> list = new ArrayList<Resource>();
        list.add(vmjar);
        download(list);

        reportTrackingEvent("jvm_unpack", -1);

        updateStatus("m.unpacking_java");
        if (!vmjar.unpack()) {
            throw new IOException("m.java_unpack_failed");
        }
        vmjar.markAsValid();

        // Sun, why dost thou spite me? Java doesn't know anything about file permissions (and by
        // extension then, neither does Jar), so on Joonix we have to hackily make java_vm/bin/java
        // executable by execing chmod; a pox on their children!
        if (!RunAnywhere.isWindows()) {
            String vmbin = LaunchUtil.LOCAL_JAVA_DIR + File.separator + "bin" +
                File.separator + "java";
            String cmd = "chmod a+rx " + _app.getLocalPath(vmbin);
            try {
                log.info("Please smack a Java engineer. Running: " + cmd);
                Runtime.getRuntime().exec(cmd);
            } catch (Exception e) {
                log.warning("Failed to mark VM binary as executable", "cmd", cmd, "error", e);
                // we should do something like tell the user or something but fucking fuck
            }
        }

        // lastly regenerate the .jsa dump file that helps Java to start up faster
        String vmpath = LaunchUtil.getJVMPath(_app.getLocalPath(""));
        try {
            log.info("Regenerating classes.jsa for " + vmpath + "...");
            Runtime.getRuntime().exec(vmpath + " -Xshare:dump");
        } catch (Exception e) {
            log.warning("Failed to regenerate .jsa dum file", "error", e);
        }

        reportTrackingEvent("jvm_complete", -1);
    }

    /**
     * Called if the application is determined to be of an old version.
     */
    protected void update ()
        throws IOException, InterruptedException
    {
        // first clear all validation markers
        _app.clearValidationMarkers();

        // attempt to download the patch files
        Resource patch = _app.getPatchResource(null);
        if (patch != null) {
            List<Resource> list = new ArrayList<Resource>();
            list.add(patch);

            // add the auxiliary group patch files for activated groups
            for (Application.AuxGroup aux : _app.getAuxGroups()) {
                if (_app.isAuxGroupActive(aux.name)) {
                    patch = _app.getPatchResource(aux.name);
                    if (patch != null) {
                        list.add(patch);
                    }
                }
            }

            // show the patch notes button, if applicable
            if (!StringUtil.isBlank(_ifc.patchNotesUrl)) {
                createInterfaceAsync(false);
                EventQueue.invokeLater(new Runnable() {
                    public void run () {
                        _patchNotes.setVisible(true);
                    }
                });
            }

            // download the patch files...
            setStep(Step.DOWNLOAD);
            download(list);

            // and apply them...
            setStep(Step.PATCH);
            updateStatus("m.patching");

            long[] sizes = new long[list.size()];
            Arrays.fill(sizes, 1L);
            ProgressAggregator pragg = new ProgressAggregator(_progobs, sizes);
            int ii = 0; for (Resource prsrc : list) {
                ProgressObserver pobs = pragg.startElement(ii++);
                try {
                    Patcher patcher = new Patcher();
                    patcher.patch(prsrc.getLocal().getParentFile(), prsrc.getLocal(), pobs);
                } catch (Exception e) {
                    log.warning("Failed to apply patch", "prsrc", prsrc, e);
                }

                // clean up the patch file
                if (!prsrc.getLocal().delete()) {
                    log.warning("Failed to delete '" + prsrc + "'.");
                    prsrc.getLocal().deleteOnExit();
                }
            }
        }

        // if the patch resource is null, that means something was booched in the application, so
        // we skip the patching process but update the metadata which will result in a "brute
        // force" upgrade

        // finally update our metadata files...
        _app.updateMetadata();
        // ...and reinitialize the application
        _ifc = _app.init(true);
    }

    /**
     * Called if the application is determined to require resource downloads.
     */
    protected void download (List<Resource> resources)
        throws IOException, InterruptedException
    {
        // create our user interface
        createInterfaceAsync(false);

        // create a downloader to download our resources
        Downloader.Observer obs = new Downloader.Observer() {
            public void resolvingDownloads () {
                updateStatus("m.resolving");
            }

            public boolean downloadProgress (int percent, long remaining) {
                // check for another getdown running at 0 and every 10% after that
                if (_lastCheck == -1 || percent >= _lastCheck + 10) {
                    if (_delay > 0) {
                        // Stop the presses if something else is holding the lock.
                        boolean locked = _app.lockForUpdates();
                        _app.releaseLock();
                        return locked;
                    }
                    _lastCheck = percent;
                }
                if (Thread.currentThread().isInterrupted()) {
                    // The applet interrupts when it stops, so abort the download and quit. Use
                    // isInterrupted so the containing code can call interrupted outside of here
                    // to check if this was the reason for aborting.
                    return false;
                }
                setStatusAsync("m.downloading", stepToGlobalPercent(percent), remaining, true);
                if (percent > 0) {
                    reportTrackingEvent("progress", percent);
                }
                return true;
            }

            public void downloadFailed (Resource rsrc, Exception e) {
                updateStatus(MessageUtil.tcompose("m.failure", e.getMessage()));
                log.warning("Download failed", "rsrc", rsrc, e);
            }

            /** The last percentage at which we checked for another getdown running, or -1 for not
             * having checked at all. */
            protected int _lastCheck = -1;
        };

        // start the download and wait for it to complete
        Downloader dl = new HTTPDownloader(resources, obs);
        if (!dl.download()) {
            if (Thread.interrupted()) {
                throw new InterruptedException("m.applet_stopped");
            }
            throw new MultipleGetdownRunning();
        }
    }

    /**
     * Called to launch the application if everything is determined to be ready to go.
     */
    protected void launch ()
    {
        setStep(Step.LAUNCH);
        setStatusAsync("m.launching", stepToGlobalPercent(100), -1L, false);

        try {
            if (invokeDirect()) {
                // if we're in applet mode, this will NOOP; if we're in app mode and are invoking
                // direct, we want to close the Getdown window, as the app is launching
                disposeContainer();
                _app.releaseLock();
                _app.invokeDirect(getApplet());

            } else {
                Process proc;
                if (_app.hasOptimumJvmArgs()) {
                    // if we have "optimum" arguments, we want to try launching with them first
                    proc = _app.createProcess(true);

                    long fallback = System.currentTimeMillis() + FALLBACK_CHECK_TIME;
                    boolean error = false;
                    while (fallback > System.currentTimeMillis()) {
                        try {
                            error = proc.exitValue() != 0;
                            break;
                        } catch (IllegalThreadStateException e) {
                            Thread.yield();
                        }
                    }

                    if (error) {
                        log.info("Failed to launch with optimum arguments; falling back.");
                        proc = _app.createProcess(false);
                    }
                } else {
                    proc = _app.createProcess(false);
                }

                // close standard in to avoid choking standard out of the launched process
                proc.getInputStream().close();
                // close standard out, since we're not going to write to anything to it anyway
                proc.getOutputStream().close();

                // on Windows 98 and ME we need to stick around and read the output of stderr lest
                // the process fill its output buffer and choke, yay!
                final InputStream stderr = proc.getErrorStream();
                if (LaunchUtil.mustMonitorChildren()) {
                    // close our window if it's around
                    disposeContainer();
                    _container = null;
                    copyStream(stderr, System.err);
                    log.info("Process exited: " + proc.waitFor());

                } else {
                    // spawn a daemon thread that will catch the early bits of stderr in case the
                    // launch fails
                    Thread t = new Thread() {
                        @Override public void run () {
                            copyStream(stderr, System.err);
                        }
                    };
                    t.setDaemon(true);
                    t.start();
                }
            }

            // if we have a UI open and we haven't been around for at least 5 seconds, don't stick
            // a fork in ourselves straight away but give our lovely user a chance to see what
            // we're doing
            long uptime = System.currentTimeMillis() - _startup;
            if (_container != null && uptime < MIN_EXIST_TIME) {
                try {
                    Thread.sleep(MIN_EXIST_TIME - uptime);
                } catch (Exception e) {
                }
            }

            // pump the percent up to 100%
            setStatusAsync(null, 100, -1L, false);
            exit(0);

            if (_playAgain != null && _playAgain.isEnabled()) {
                // wait a little time before showing the button
                Timer timer = new Timer("playAgain", true);
                timer.schedule(new TimerTask() {
                    @Override public void run () {
                        initPlayAgain();
                        _playAgain.setVisible(true);
                    }
                }, PLAY_AGAIN_TIME);
            }

        } catch (Exception e) {
            log.warning("launch() failed.", e);
        }
    }

    /**
     * Creates our user interface, which we avoid doing unless we actually have to update
     * something. NOTE: this happens on the next UI tick, not immediately.
     *
     * @param reinit - if the interface should be reinitialized if it already exists.
     */
    protected void createInterfaceAsync (final boolean reinit)
    {
        if (_silent || (_container != null && !reinit)) {
            return;
        }

        EventQueue.invokeLater(new Runnable() {
            public void run () {
                if (_container == null || reinit) {
                    if (_container == null) {
                        _container = createContainer();
                    } else {
                        _container.removeAll();
                    }
                    _layers = new JLayeredPane();
                    _container.add(_layers, BorderLayout.CENTER);
                    _patchNotes = new JButton(new AbstractAction(_msgs.getString("m.patch_notes")) {
                        @Override public void actionPerformed (ActionEvent event) {
                            showDocument(_ifc.patchNotesUrl);
                        }
                    });
                    _patchNotes.setFont(StatusPanel.FONT);
                    _layers.add(_patchNotes);

                    if (getApplet() != null) {
                        _playAgain = new JButton();
                        _playAgain.setEnabled(false);
                        _playAgain.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                        _playAgain.setFont(StatusPanel.FONT);
                        _playAgain.addActionListener(new ActionListener() {
                            @Override public void actionPerformed (ActionEvent event) {
                                _playAgain.setVisible(false);
                                _stepMinPercent = _lastGlobalPercent = 0;
                                EventQueue.invokeLater(new Runnable() {
                                    public void run () {
                                        getdown();
                                    }
                                });
                            }
                        });
                        _layers.add(_playAgain);
                    }

                    _status = new StatusPanel(_msgs);
                    _layers.add(_status);
                    initInterface();
                }
                showContainer();
            }
        });
    }

    /**
     * Initializes the interface with the current UpdateInterface and backgrounds.
     */
    protected void initInterface ()
    {
        RotatingBackgrounds newBackgrounds = getBackground();
        if (_background == null || newBackgrounds.getNumImages() > 0) {
            // Leave the old _background in place if there is an old one to leave in place
            // and the new getdown.txt didn't yield any images.
            _background = newBackgrounds;
        }
        _status.init(_ifc, _background, getProgressImage());
        Dimension size = _status.getPreferredSize();
        _status.setSize(size);
        _layers.setPreferredSize(size);

        _patchNotes.setBounds(_ifc.patchNotes);
        _patchNotes.setVisible(false);

        initPlayAgain();

        // we were displaying progress while the UI wasn't up. Now that it is, whatever progress
        // is left is scaled into a 0-100 DISPLAYED progress.
        _uiDisplayPercent = _lastGlobalPercent;
        _stepMinPercent = _lastGlobalPercent = 0;
    }

    protected void initPlayAgain ()
    {
        if (_playAgain != null) {
            Image image = loadImage(_ifc.playAgainImage);
            boolean hasImage = image != null;
            if (hasImage) {
                _playAgain.setIcon(new ImageIcon(image));
                _playAgain.setText("");
            } else {
                _playAgain.setText(_msgs.getString("m.play_again"));
                _playAgain.setIcon(null);
            }
            _playAgain.setBorderPainted(!hasImage);
            _playAgain.setOpaque(!hasImage);
            _playAgain.setContentAreaFilled(!hasImage);
            if (_ifc.playAgain != null) {
                _playAgain.setBounds(_ifc.playAgain);
                _playAgain.setEnabled(true);
            }
            _playAgain.setVisible(false);
        }
    }

    protected RotatingBackgrounds getBackground ()
    {
        if (_ifc.rotatingBackgrounds != null) {
            if (_ifc.backgroundImage != null) {
                log.warning("ui.background_image and ui.rotating_background were both specified. " +
                            "The rotating images are being used.");
            }
            return new RotatingBackgrounds(_ifc.rotatingBackgrounds, _ifc.errorBackground,
                Getdown.this);
        } else if (_ifc.backgroundImage != null) {
            return new RotatingBackgrounds(loadImage(_ifc.backgroundImage));
        } else {
            return new RotatingBackgrounds();
        }
    }

    protected Image getProgressImage ()
    {
        return loadImage(_ifc.progressImage);
    }

    protected void handleWindowClose ()
    {
        if (_dead) {
            exit(0);
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

    /**
     * Update the status to indicate getdown has failed for the reason in <code>message</code>.
     */
    protected void fail (String message)
    {
        _dead = true;
        setStatusAsync(message, stepToGlobalPercent(0), -1L, true);
    }

    /**
     * Set the current step, which will be used to globalize per-step percentages.
     */
    protected void setStep (Step step)
    {
        int finalPercent = -1;
        for (Integer perc : _ifc.stepPercentages.get(step)) {
            if (perc > _stepMaxPercent) {
                finalPercent = perc;
                break;
            }
        }
        if (finalPercent == -1) {
            // we've gone backwards and this step will be ignored
            return;
        }

        _stepMaxPercent = finalPercent;
        _stepMinPercent = _lastGlobalPercent;
    }

    /**
     * Convert a step percentage to the global percentage.
     */
    protected int stepToGlobalPercent (int percent)
    {
        int adjustedMaxPercent =
            ((_stepMaxPercent - _uiDisplayPercent) * 100) / (100 - _uiDisplayPercent);
        _lastGlobalPercent = Math.max(_lastGlobalPercent,
            _stepMinPercent + (percent * (adjustedMaxPercent - _stepMinPercent)) / 100);
        return _lastGlobalPercent;
    }

    /**
     * Updates the status. NOTE: this happens on the next UI tick, not immediately.
     */
    protected void setStatusAsync (final String message, final int percent, final long remaining,
                                   boolean createUI)
    {
        if (_status == null && createUI) {
            createInterfaceAsync(false);
        }

        EventQueue.invokeLater(new Runnable() {
            public void run () {
                if (_status == null) {
                    if (message != null) {
                        log.info("Dropping status '" + message + "'.");
                    }
                    return;
                }
                if (message != null) {
                    _status.setStatus(message, _dead);
                }
                if (_dead) {
                    _status.setProgress(0, -1L);
                } else if (percent >= 0) {
                    _status.setProgress(percent, remaining);
                }
            }
        });
    }

    protected void reportTrackingEvent (String event, int progress)
    {
        if (!_enableTracking) {
            return;

        } else if (progress > 0) {
            // we need to make sure we do the right thing if we skip over progress levels
            do {
                URL url = _app.getTrackingProgressURL(++_reportedProgress);
                if (url != null) {
                    new ProgressReporter(url).start();
                }
            } while (_reportedProgress <= progress);

        } else {
            URL url = _app.getTrackingURL(event);
            if (url != null) {
                new ProgressReporter(url).start();
            }
        }
    }

    /**
     * Creates the container in which our user interface will be displayed.
     */
    protected abstract Container createContainer ();

    /**
     * Shows the container in which our user interface will be displayed.
     */
    protected abstract void showContainer ();

    /**
     * Disposes the container in which we have our user interface.
     */
    protected abstract void disposeContainer ();

    /**
     * If this method returns true we will run the application in the same JVM, otherwise we will
     * fork off a new JVM. Some options are not supported if we do not fork off a new JVM.
     */
    protected boolean invokeDirect ()
    {
        // by default check a sysprop (which itself defaults to false); in applet mode this is
        // overridden to check the applet config
        return SysProps.direct();
    }

    /**
     * Provides access to the applet that we'll pass on to our application when we're in "invoke
     * direct" mode.
     */
    protected JApplet getApplet ()
    {
        return null;
    }

    /**
     * Requests to show the document at the specified URL in a new window.
     */
    protected abstract void showDocument (String url);

    /**
     * Requests that Getdown exit. In applet mode this does nothing.
     */
    protected abstract void exit (int exitCode);

    /**
     * Copies the supplied stream from the specified input to the specified output. Used to copy
     * our child processes stderr and stdout to our own stderr and stdout.
     */
    protected static void copyStream (InputStream in, PrintStream out)
    {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String line;
            while ((line = reader.readLine()) != null) {
                out.print(line);
                out.flush();
            }
        } catch (IOException ioe) {
            log.warning("Failure copying", "in", in, "out", out, "error", ioe);
        }
    }

    /** Used to fetch a progress report URL. */
    protected class ProgressReporter extends Thread
    {
        public ProgressReporter (URL url) {
            setDaemon(true);
            _url = url;
        }

        @Override
        public void run () {
            try {
                HttpURLConnection ucon = ConnectionUtil.openHttp(_url);

                // if we have a tracking cookie configured, configure the request with it
                if (_app.getTrackingCookieName() != null &&
                    _app.getTrackingCookieProperty() != null) {
                    String val = System.getProperty(_app.getTrackingCookieProperty());
                    if (val != null) {
                        ucon.setRequestProperty("Cookie", _app.getTrackingCookieName() + "=" + val);
                    }
                }

                // now request our tracking URL and ensure that we get a non-error response
                ucon.connect();
                try {
                    if (ucon.getResponseCode() != HttpURLConnection.HTTP_OK) {
                        log.warning("Failed to report tracking event",
                            "url", _url, "rcode", ucon.getResponseCode());
                    }
                } finally {
                    ucon.disconnect();
                }

            } catch (IOException ioe) {
                log.warning("Failed to report tracking event", "url", _url, "error", ioe);
            }
        }

        protected URL _url;
    }

    /** Used to pass progress on to our user interface. */
    protected ProgressObserver _progobs = new ProgressObserver() {
        public void progress (int percent) {
            setStatusAsync(null, stepToGlobalPercent(percent), -1L, false);
        }
    };

    protected Application _app;
    protected Application.UpdateInterface _ifc = new Application.UpdateInterface();

    protected ResourceBundle _msgs;
    protected Container _container;
    protected JLayeredPane _layers;
    protected StatusPanel _status;
    protected JButton _patchNotes;
    protected JButton _playAgain;
    protected AbortPanel _abort;
    protected RotatingBackgrounds _background;

    protected boolean _dead;
    protected boolean _silent;
    protected boolean _launchInSilent;
    protected long _startup;

    protected boolean _enableTracking = true;
    protected int _reportedProgress = 0;

    /** Number of minutes to wait after startup before beginning any real heavy lifting. */
    protected int _delay;

    protected int _stepMaxPercent;
    protected int _stepMinPercent;
    protected int _lastGlobalPercent;
    protected int _uiDisplayPercent;

    protected static final int MAX_LOOPS = 5;
    protected static final long MIN_EXIST_TIME = 5000L;
    protected static final long FALLBACK_CHECK_TIME = 1000L;
    protected static final long PLAY_AGAIN_TIME = 3000L;
    protected static final String PROXY_REGISTRY =
        "Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings";
}
