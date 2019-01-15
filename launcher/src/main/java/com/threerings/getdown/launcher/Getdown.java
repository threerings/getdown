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
import java.util.*;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLayeredPane;

import com.samskivert.swing.util.SwingUtil;
import com.threerings.getdown.data.*;
import com.threerings.getdown.data.Application.UpdateInterface.Step;
import com.threerings.getdown.net.Downloader;
import com.threerings.getdown.net.HTTPDownloader;
import com.threerings.getdown.tools.Patcher;
import com.threerings.getdown.util.*;

import static com.threerings.getdown.Log.log;

/**
 * Manages the main control for the Getdown application updater and deployment system.
 */
public abstract class Getdown extends Thread
    implements Application.StatusDisplay, RotatingBackgrounds.ImageLoader
{
    public Getdown (EnvConfig envc)
    {
        super("Getdown");
        try {
            // If the silent property exists, install without bringing up any gui. If it equals
            // launch, start the application after installing. Otherwise, just install and exit.
            _silent = SysProps.silent();
            if (_silent) {
                _launchInSilent = SysProps.launchInSilent();
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
            if (dir.equals(".")) {
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
        } else if (_readyToInstall) {
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

    @Override
    public void run ()
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
                configureContainer();
                ProxyPanel panel = new ProxyPanel(this, _msgs);
                // set up any existing configured proxy
                String[] hostPort = ProxyUtil.loadProxy(_app);
                panel.setProxy(hostPort[0], hostPort[1]);
                _container.add(panel, BorderLayout.CENTER);
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
    public void configProxy (String host, String port, String username, String password)
    {
        log.info("User configured proxy", "host", host, "port", port);

        if (!StringUtil.isBlank(host)) {
            ProxyUtil.configProxy(_app, host, port, username, password);
        }

        // clear out our UI
        disposeContainer();
        _container = null;

        // fire up a new thread
        new Thread(this).start();
    }

    protected boolean detectProxy () {
        if (ProxyUtil.autoDetectProxy(_app)) {
            return true;
        }

        // otherwise see if we actually need a proxy; first we have to initialize our application
        // to get some sort of interface configuration and the appbase URL
        log.info("Checking whether we need to use a proxy...");
        try {
            readConfig(true);
        } catch (IOException ioe) {
            // no worries
        }
        updateStatus("m.detecting_proxy");
        if (!ProxyUtil.canLoadWithoutProxy(_app.getConfigResource().getRemote())) {
            return false;
        }

        // we got through, so we appear not to require a proxy; make a blank proxy config so that
        // we don't go through this whole detection process again next time
        log.info("No proxy appears to be needed.");
        ProxyUtil.saveProxy(_app, null, null);
        return true;
    }

    protected void readConfig (boolean preloads) throws IOException {
        Config config = _app.init(true);
        if (preloads) doPredownloads(_app.getResources());
        _ifc = new Application.UpdateInterface(config);
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

        try {
            download(predownloads);
            for (Resource rsrc : predownloads) {
                rsrc.install(false); // install but don't validate yet
            }
        } catch (IOException ioe) {
            log.warning("Failed to predownload resources. Continuing...", ioe);
        }
    }

    /**
     * Does the actual application validation, update and launching business.
     */
    protected void getdown ()
    {

        try {
            // first parses our application deployment file
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
                    for (Resource r : toDownload) {
                        if (!_toInstallResources.contains(r)) {
                            _toInstallResources.add(r);
                        }
                    }

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

        reportTrackingEvent("jvm_start", -1);

        updateStatus("m.downloading_java");
        List<Resource> list = new ArrayList<>();
        list.add(vmjar);
        download(list);

        reportTrackingEvent("jvm_unpack", -1);

        updateStatus("m.unpacking_java");
        vmjar.install(true);

        // these only run on non-Windows platforms, so we use Unix file separators
        String localJavaDir = LaunchUtil.LOCAL_JAVA_DIR + "/";
        FileUtil.makeExecutable(_app.getLocalPath(localJavaDir + "bin/java"));
        FileUtil.makeExecutable(_app.getLocalPath(localJavaDir + "lib/jspawnhelper"));
        FileUtil.makeExecutable(_app.getLocalPath(localJavaDir + "lib/amd64/jspawnhelper"));

        // lastly regenerate the .jsa dump file that helps Java to start up faster
        String vmpath = LaunchUtil.getJVMPath(_app.getLocalPath(""));
        try {
            log.info("Regenerating classes.jsa for " + vmpath + "...");
            Runtime.getRuntime().exec(vmpath + " -Xshare:dump");
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

        Downloader dl = new HTTPDownloader(_app.proxy) {
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
                    Thread.sleep(minshow - uptime);
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
    protected class ProgressReporter extends Thread
    {
        public ProgressReporter (URL url) {
            setDaemon(true);
            _url = url;
        }

        @Override
        public void run () {
            try {
                HttpURLConnection ucon = ConnectionUtil.openHttp(_app.proxy, _url, 0, 0);

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
