//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2018 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.launcher;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLayeredPane;

import com.samskivert.swing.util.SwingUtil;
import com.threerings.getdown.data.Application;
import com.threerings.getdown.data.Application.UpdateInterface.Step;
import com.threerings.getdown.data.Build;
import com.threerings.getdown.data.EnvConfig;
import com.threerings.getdown.data.Resource;
import com.threerings.getdown.data.SysProps;
import com.threerings.getdown.net.Downloader;
import com.threerings.getdown.tools.Patcher;
import com.threerings.getdown.util.Config;
import com.threerings.getdown.util.FileUtil;
import com.threerings.getdown.util.LaunchUtil;
import com.threerings.getdown.util.MessageUtil;
import com.threerings.getdown.util.ProgressAggregator;
import com.threerings.getdown.util.ProgressObserver;
import com.threerings.getdown.util.StringUtil;
import com.threerings.getdown.util.VersionUtil;
import static com.threerings.getdown.Log.log;

/**
 * Manages the main control for the Getdown application updater and deployment system.
 */
public abstract class Getdown
    implements Application.StatusDisplay, RotatingBackgrounds.ImageLoader
{
    /**
     * Starts a thread to run Getdown and ultimately (hopefully) launch the target app.
     */
    public static void run (final Getdown getdown) {
        new Thread("Getdown") {
            @Override public void run () {
                getdown.run();
            }
        }.start();
    }

    public Getdown (EnvConfig envc)
    {
        try {
            // If the silent property exists, install without bringing up any gui. If it equals
            // launch, start the application after installing. Otherwise, just install and exit.
            _silent = SysProps.silent();
            if (_silent) {
                _launchInSilent = SysProps.launchInSilent();
                _noUpdate = SysProps.noUpdate();
            }
            // If we're running in a headless environment and have not otherwise customized
            // silence, operate without a UI and do launch the app.
            if (!_silent && GraphicsEnvironment.isHeadless()) {
                log.info("Running in headless JVM, will attempt to operate without UI.");
                _silent = true;
                _launchInSilent = true;
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
            String dir = envc.appDir.toString();
            if (".".equals(dir)) {
                dir = System.getProperty("user.dir");
            }
            String errmsg = "The directory in which this application is installed:\n" + dir +
                "\nis invalid (" + e.getMessage() + "). If the full path to the app directory " +
                "contains the '!' character, this will trigger this error.";
            fail(errmsg);
        }
        _app = new Application(envc);
        _startup = System.currentTimeMillis();
    }

    /**
     * Returns true if there are pending new resources, waiting to be installed.
     */
    public boolean isUpdateAvailable ()
    {
        return _readyToInstall && !_toInstallResources.isEmpty();
    }

    /**
     * Installs the currently pending new resources.
     */
    public void install () throws IOException
    {
        if (SysProps.noInstall()) {
            log.info("Skipping install due to 'no_install' sysprop.");
        } else if (isUpdateAvailable()) {
            log.info("Installing " + _toInstallResources.size() + " downloaded resources:");
            for (Resource resource : _toInstallResources) {
                resource.install(true);
            }
            _toInstallResources.clear();
            _readyToInstall = false;
            log.info("Install completed.");
        } else {
            log.info("Nothing to install.");
        }
    }

    /**
     * Configures our proxy settings (called by {@link ProxyPanel}) and fires up the launcher.
     */
    public void configProxy (String host, String port, String username, String password)
    {
        log.info("User configured proxy", "host", host, "port", port);
        ProxyUtil.configProxy(_app, host, port, username, password);

        // clear out our UI
        disposeContainer();
        _container = null;

        // fire up a new thread
        run(this);
    }

    /**
     * The main entry point of Getdown: does some sanity checks and preparation, then delegates the
     * actual getting down to {@link #getdown}. This is not called directly, but rather via the
     * static {@code run} method as Getdown does its main work on a separate thread.
     */
    protected void run ()
    {
        // if we have no messages, just bail because we're hosed; the error message will be
        // displayed to the user already
        if (_msgs == null) {
            return;
        }

        log.info("Getdown starting", "version", Build.version(), "built", Build.time());

        // determine whether or not we can write to our install directory
        File instdir = _app.getLocalPath("");
        if (!instdir.canWrite()) {
            String path = instdir.getPath();
            if (".".equals(path)) {
                path = System.getProperty("user.dir");
            }
            fail(MessageUtil.tcompose("m.readonly_error", path));
            return;
        }

        _dead = false;
        // if we fail to detect a proxy, but we're allowed to run offline, then go ahead and
        // run the app anyway because we're prepared to cope with not being able to update
        if (detectProxy() || _app.allowOffline()) getdown();
        else requestProxyInfo(false);
    }

    protected boolean detectProxy () {
        boolean tryNoProxy = SysProps.tryNoProxyFirst();
        if (!tryNoProxy && ProxyUtil.autoDetectProxy(_app)) {
            return true;
        }

        // see if we actually need a proxy; first we have to initialize our application
        // to get some sort of interface configuration and the appbase URL
        log.info("Checking whether we need to use a proxy...");
        try {
            readConfig(true);
        } catch (IOException ioe) {
            // no worries
        }
        updateStatus("m.detecting_proxy");
        URL configURL = _app.getConfigResource().getRemote();
        if (!ProxyUtil.canLoadWithoutProxy(configURL, tryNoProxy ? 2 : 5)) {
            // if we didn't auto-detect proxy first thing, do auto-detect now
            return tryNoProxy ? ProxyUtil.autoDetectProxy(_app) : false;
        }

        log.info("No proxy appears to be needed.");
        if (!tryNoProxy)  {
            // we got through, so we appear not to require a proxy; make a blank proxy config so
            // that we don't go through this whole detection process again next time
            ProxyUtil.saveProxy(_app, null, null);
        }
        return true;
    }

    protected void readConfig (boolean preloads) throws IOException {
        Config config = _app.init(true);
        if (preloads) doPredownloads(_app.getResources());
        _ifc = new Application.UpdateInterface(config);
    }

    protected void requestProxyInfo (boolean reinitAuth) {
        if (_silent) {
            log.warning("Need a proxy, but we don't want to bother anyone. Exiting.");
            return;
        }

        // create a panel they can use to configure the proxy settings
        _container = createContainer();
        // allow them to close the window to abort the proxy configuration
        _dead = true;
        configureContainer();
        ProxyPanel panel = new ProxyPanel(this, _msgs, reinitAuth);
        // set up any existing configured proxy
        String[] hostPort = ProxyUtil.loadProxy(_app);
        panel.setProxy(hostPort[0], hostPort[1]);
        _container.add(panel, BorderLayout.CENTER);
        showContainer();
    }

    /**
     * Downloads and installs (without verifying) any resources that are marked with a
     * {@code PRELOAD} attribute.
     * @param resources the full set of resources from the application (the predownloads will be
     * extracted from it).
     */
    protected void doPredownloads (Collection<Resource> resources) {
        List<Resource> predownloads = new ArrayList<>();
        for (Resource rsrc : resources) {
            if (rsrc.shouldPredownload() && !rsrc.getLocal().exists()) {
                predownloads.add(rsrc);
            }
        }

        if (!predownloads.isEmpty()) {
            try {
                download(predownloads);
                for (Resource rsrc : predownloads) {
                    rsrc.install(false); // install but don't validate yet
                }
            } catch (IOException ioe) {
                log.warning("Failed to predownload resources. Continuing...", ioe);
            }
        }
    }

    /**
     * Does the actual application validation, update and launching business.
     */
    protected void getdown ()
    {
        try {
            // first parse our application deployment file
            try {
                readConfig(true);
            } catch (IOException ioe) {
                log.warning("Failed to initialize: " + ioe);
                _app.attemptRecovery(this);
                // and re-initalize
                readConfig(true);
                // and force our UI to be recreated with the updated info
                createInterfaceAsync(true);
            }
            if (!_noUpdate && !_app.lockForUpdates()) {
                throw new MultipleGetdownRunning();
            }

            // update the config modtime so a sleeping getdown will notice the change
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
                TimeUnit.MINUTES.sleep(_delay);
                if (lastConfigModtime < config.lastModified()) {
                    log.warning("getdown.txt was modified while getdown was waiting.");
                    throw new MultipleGetdownRunning();
                }
            }

            // if no_update was specified, directly start the app without updating
            if (_noUpdate) {
                log.info("Launching without update!");
                launch();
                return;
            }

            // we create this tracking counter here so that we properly note the first time through
            // the update process whether we previously had validated resources (which means this
            // is not a first time install); we may, in the course of updating, wipe out our
            // validation markers and revalidate which would make us think we were doing a fresh
            // install if we didn't specifically remember that we had validated resources the first
            // time through
            int[] alreadyValid = new int[1];

            // we'll keep track of all the resources we unpack
            Set<Resource> unpacked = new HashSet<>();

            _toInstallResources = new HashSet<>();
            _readyToInstall = false;

            // setStep(Step.START);
            for (int ii = 0; ii < MAX_LOOPS; ii++) {
                // make sure we have the desired version and that the metadata files are valid...
                setStep(Step.VERIFY_METADATA);
                setStatusAsync("m.validating", -1, -1L, false);
                if (_app.verifyMetadata(this)) {
                    log.info("Application requires update.");
                    update();
                    // loop back again and reverify the metadata
                    continue;
                }

                // now verify (and download) our resources...
                setStep(Step.VERIFY_RESOURCES);
                setStatusAsync("m.validating", -1, -1L, false);
                Set<Resource> toDownload = new HashSet<>();
                _app.verifyResources(_progobs, alreadyValid, unpacked,
                                     _toInstallResources, toDownload);

                if (toDownload.size() > 0) {
                    // we have resources to download, also note them as to-be-installed
                    _toInstallResources.addAll(toDownload);

                    try {
                        // if any of our resources have already been marked valid this is not a
                        // first time install and we don't want to enable tracking
                        _enableTracking = (alreadyValid[0] == 0);
                        reportTrackingEvent("app_start", -1);

                        // redownload any that are corrupt or invalid...
                        log.info(toDownload.size() + " of " + _app.getAllActiveResources().size() +
                                 " rsrcs require update (" + alreadyValid[0] + " assumed valid).");
                        setStep(Step.REDOWNLOAD_RESOURCES);
                        download(toDownload);

                        reportTrackingEvent("app_complete", -1);

                    } finally {
                        _enableTracking = false;
                    }

                    // now we'll loop back and try it all again
                    continue;
                }

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
                        log.info("Performing unpack", "version", version, "aversion", aversion);
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

                // assuming we're not doing anything funny, install the update
                _readyToInstall = true;
                install();

                // Only launch if we aren't in silent mode. Some mystery program starting out
                // of the blue would be disconcerting.
                if (!_silent || _launchInSilent) {
                    // And another final check for the lock. It'll already be held unless
                    // we're in silent mode.
                    _app.lockForUpdates();
                    launch();
                }
                return;
            }

            log.warning("Pants! We couldn't get the job done.");
            throw new IOException("m.unable_to_repair");

        } catch (Exception e) {
            // if we failed due to proxy errors, ask for proxy info
            switch (_app.conn.state) {
            case NEED_PROXY:
                requestProxyInfo(false);
                break;
            case NEED_PROXY_AUTH:
                requestProxyInfo(true);
                break;
            default:
                log.warning("getdown() failed.", e);
                fail(e);
                _app.releaseLock();
                break;
            }
        }
    }

    // documentation inherited from interface
    @Override
    public void updateStatus (String message)
    {
        setStatusAsync(message, -1, -1L, true);
    }

    /**
     * Load the image at the path. Before trying the exact path/file specified we will look to see
     * if we can find a localized version by sticking a {@code _<language>} in front of the "." in
     * the filename.
     */
    @Override
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
        throws IOException
    {
        Resource vmjar = _app.getJavaVMResource();
        if (vmjar == null) {
            throw new IOException("m.java_download_failed");
        }

        // on Windows, if the local JVM is in use, we will not be able to replace it with an
        // updated JVM; we detect this by attempting to rename the java.dll to its same name, which
        // will fail on Windows for in use files; hackery!
        File javaLocalDir = _app.getJavaLocalDir();
        File javaDll = new File(javaLocalDir, "bin" + File.separator + "java.dll");
        if (javaDll.exists()) {
            if (!javaDll.renameTo(javaDll)) {
                log.info("Cannot update local Java VM as it is in use.");
                return;
            }
        }

        reportTrackingEvent("jvm_start", -1);

        updateStatus("m.downloading_java");
        List<Resource> list = new ArrayList<>();
        list.add(vmjar);
        download(list);

        reportTrackingEvent("jvm_unpack", -1);
        updateStatus("m.unpacking_java");
        try {
            vmjar.install(true);
        } catch (IOException ioe) {
            throw new IOException("m.java_unpack_failed", ioe);
        }

        // these only run on non-Windows platforms, so we use Unix file separators
        FileUtil.makeExecutable(new File(javaLocalDir, "bin/java"));
        FileUtil.makeExecutable(new File(javaLocalDir, "lib/jspawnhelper"));
        FileUtil.makeExecutable(new File(javaLocalDir, "lib/amd64/jspawnhelper"));

        // lastly regenerate the .jsa dump file that helps Java to start up faster
        String vmpath = LaunchUtil.getJVMBinaryPath(javaLocalDir, false);
        String[] command = { vmpath, "-Xshare:dump" };
        try {
            log.info("Regenerating classes.jsa for " + vmpath + "...");
            Runtime.getRuntime().exec(command);
        } catch (Exception e) {
            log.warning("Failed to regenerate .jsa dump file", "error", e);
        }

        reportTrackingEvent("jvm_complete", -1);
    }

    /**
     * Called if the application is determined to be of an old version.
     */
    protected void update ()
        throws IOException
    {
        // first clear all validation markers
        _app.clearValidationMarkers();

        // attempt to download the patch files
        Resource patch = _app.getPatchResource(null);
        if (patch != null) {
            List<Resource> list = new ArrayList<>();
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
                    // install the patch file (renaming them from _new)
                    prsrc.install(false);
                    // now apply the patch
                    Patcher patcher = new Patcher();
                    patcher.patch(prsrc.getLocal().getParentFile(), prsrc.getLocal(), pobs);
                } catch (Exception e) {
                    log.warning("Failed to apply patch", "prsrc", prsrc, e);
                }

                // clean up the patch file
                if (!FileUtil.deleteHarder(prsrc.getLocal())) {
                    log.warning("Failed to delete '" + prsrc + "'.");
                }
            }
        }

        // if the patch resource is null, that means something was booched in the application, so
        // we skip the patching process but update the metadata which will result in a "brute
        // force" upgrade

        // finally update our metadata files...
        _app.updateMetadata();
        // ...and reinitialize the application
        readConfig(false);
    }

    /**
     * Called if the application is determined to require resource downloads.
     */
    protected void download (Collection<Resource> resources)
        throws IOException
    {
        // create our user interface
        createInterfaceAsync(false);

        Downloader dl = new Downloader(_app.conn) {
            @Override protected void resolvingDownloads () {
                updateStatus("m.resolving");
            }

            @Override protected void downloadProgress (int percent, long remaining) {
                // check for another getdown running at 0 and every 10% after that
                if (_lastCheck == -1 || percent >= _lastCheck + 10) {
                    if (_delay > 0) {
                        // stop the presses if something else is holding the lock
                        boolean locked = _app.lockForUpdates();
                        _app.releaseLock();
                        if (locked) abort();
                    }
                    _lastCheck = percent;
                }
                setStatusAsync("m.downloading", stepToGlobalPercent(percent), remaining, true);
                if (percent > 0) {
                    reportTrackingEvent("progress", percent);
                }
            }

            @Override protected void downloadFailed (Resource rsrc, Exception e) {
                updateStatus(MessageUtil.tcompose("m.failure", e.getMessage()));
                log.warning("Download failed", "rsrc", rsrc, e);
            }

            /** The last percentage at which we checked for another getdown running, or -1 for not
             * having checked at all. */
            protected int _lastCheck = -1;
        };
        if (!dl.download(resources, _app.maxConcurrentDownloads())) {
            // if we aborted due to detecting another getdown running, we want to report here
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
                // we want to close the Getdown window, as the app is launching
                disposeContainer();
                _app.releaseLock();
                _app.invokeDirect();

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

            // if we have a UI open and we haven't been around for at least 5 seconds (the default
            // for min_show_seconds), don't stick a fork in ourselves straight away but give our
            // lovely user a chance to see what we're doing
            long uptime = System.currentTimeMillis() - _startup;
            long minshow = _ifc.minShowSeconds * 1000L;
            if (_container != null && uptime < minshow) {
                try {
                    TimeUnit.MILLISECONDS.sleep(minshow - uptime);
                } catch (Exception e) {
                }
            }

            // pump the percent up to 100%
            setStatusAsync(null, 100, -1L, false);
            exit(0);

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
                    configureContainer();
                    _layers = new JLayeredPane();
                    _container.add(_layers, BorderLayout.CENTER);
                    _patchNotes = new JButton(new AbstractAction(_msgs.getString("m.patch_notes")) {
                        @Override public void actionPerformed (ActionEvent event) {
                            showDocument(_ifc.patchNotesUrl);
                        }
                    });
                    _patchNotes.setFont(StatusPanel.FONT);
                    _layers.add(_patchNotes);
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

        _patchNotes.setBounds(_ifc.patchNotes.x, _ifc.patchNotes.y,
                              _ifc.patchNotes.width, _ifc.patchNotes.height);
        _patchNotes.setVisible(false);

        // we were displaying progress while the UI wasn't up. Now that it is, whatever progress
        // is left is scaled into a 0-100 DISPLAYED progress.
        _uiDisplayPercent = _lastGlobalPercent;
        _stepMinPercent = _lastGlobalPercent = 0;
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

    private void fail (Exception e) {
        String msg = e.getMessage();
        if (msg == null) {
            msg = MessageUtil.compose("m.unknown_error", _ifc.installError);
        } else if (!msg.startsWith("m.")) {
            // try to do something sensible based on the type of error
            msg = MessageUtil.taint(msg);
            msg = e instanceof FileNotFoundException ?
                MessageUtil.compose("m.missing_resource", msg, _ifc.installError) :
                MessageUtil.compose("m.init_error", msg, _ifc.installError);
        }
        // since we're dead, clear off the 'time remaining' label along with displaying the error
        fail(msg);
    }

    /**
     * Update the status to indicate getdown has failed for the reason in {@code message}.
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
                    reportProgress(url);
                }
            } while (_reportedProgress <= progress);

        } else {
            URL url = _app.getTrackingURL(event);
            if (url != null) {
                reportProgress(url);
            }
        }
    }

    /**
     * Creates the container in which our user interface will be displayed.
     */
    protected abstract Container createContainer ();

    /**
     * Configures the interface container based on the latest UI config.
     */
    protected abstract void configureContainer ();

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
        return SysProps.direct();
    }

    /**
     * Requests to show the document at the specified URL in a new window.
     */
    protected abstract void showDocument (String url);

    /**
     * Requests that Getdown exit.
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
    protected void reportProgress (final URL url) {
        Thread reporter = new Thread("Progress reporter") {
            public void run () {
                try {
                    HttpURLConnection ucon = _app.conn.openHttp(url, 0, 0);

                    // if we have a tracking cookie configured, configure the request with it
                    if (_app.getTrackingCookieName() != null &&
                        _app.getTrackingCookieProperty() != null) {
                        String val = System.getProperty(_app.getTrackingCookieProperty());
                        if (val != null) {
                            ucon.setRequestProperty(
                                "Cookie", _app.getTrackingCookieName() + "=" + val);
                        }
                    }

                    // now request our tracking URL and ensure that we get a non-error response
                    ucon.connect();
                    try {
                        if (ucon.getResponseCode() != HttpURLConnection.HTTP_OK) {
                            log.warning("Failed to report tracking event",
                                        "url", url, "rcode", ucon.getResponseCode());
                        }
                    } finally {
                        ucon.disconnect();
                    }

                } catch (IOException ioe) {
                    log.warning("Failed to report tracking event", "url", url, "error", ioe);
                }
            }
        };
        reporter.setDaemon(true);
        reporter.start();
    }

    /** Used to pass progress on to our user interface. */
    protected ProgressObserver _progobs = new ProgressObserver() {
        public void progress (int percent) {
            setStatusAsync(null, stepToGlobalPercent(percent), -1L, false);
        }
    };

    protected Application _app;
    protected Application.UpdateInterface _ifc = new Application.UpdateInterface(Config.EMPTY);

    protected ResourceBundle _msgs;
    protected Container _container;
    protected JLayeredPane _layers;
    protected StatusPanel _status;
    protected JButton _patchNotes;
    protected AbortPanel _abort;
    protected RotatingBackgrounds _background;

    protected boolean _dead;
    protected boolean _silent;
    protected boolean _launchInSilent;
    protected boolean _noUpdate;
    protected long _startup;

    protected Set<Resource> _toInstallResources;
    protected boolean _readyToInstall;

    protected boolean _enableTracking = true;
    protected int _reportedProgress = 0;

    /** Number of minutes to wait after startup before beginning any real heavy lifting. */
    protected int _delay;

    protected int _stepMaxPercent;
    protected int _stepMinPercent;
    protected int _lastGlobalPercent;
    protected int _uiDisplayPercent;

    protected static final int MAX_LOOPS = 5;
    protected static final long FALLBACK_CHECK_TIME = 1000L;
}
