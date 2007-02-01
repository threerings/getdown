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

import java.util.Locale;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.Image;
import java.awt.image.BufferedImage;

import javax.imageio.ImageIO;
import javax.swing.JApplet;
import javax.swing.JFrame;

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
 * Manages the main control for the Getdown application updater and deployment system.
 */
public abstract class Getdown extends Thread
    implements Application.StatusDisplay
{
    public static void main (String[] args)
    {
        // legacy support
        GetdownApp.main(args);
    }

    public Getdown (File appDir, String appId)
    {
        super("Getdown");
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
                "\nis invalid. The directory must not contain the '!' character. Please reinstall.";
            updateStatus(errmsg);
            _dead = true;
        }
        _app = new Application(appDir, appId);
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
            createInterface(true);
        } catch (Exception e) {
            Log.warning("Failed to preinit: " + e);
            createInterface(true);
        }
    }

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
                _container = createContainer();
                _container.add(new ProxyPanel(this, _msgs), BorderLayout.CENTER);
                showContainer();
                // allow them to close the window to abort the proxy configuration
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
                    msg = MessageUtil.compose(
                        "m.missing_resource", MessageUtil.taint(msg), _ifc.installError);
                } else {
                    msg = MessageUtil.compose(
                        "m.init_error", MessageUtil.taint(msg), _ifc.installError);
                }
            }
            updateStatus(msg);
            _dead = true;
        }
    }

    /**
     * Configures our proxy settings (called by {@link ProxyPanel}) and fires up the launcher.
     */
    public void configureProxy (String host, String port)
    {
        Log.info("User configured proxy [host=" + host + ", port=" + port + "].");

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
                Log.warning("Error creating proxy file '" + pfile + "': " + ioe);
            }

            // also configure them in the JVM
            setProxyProperties(host, port);
        }

        // clear out our UI
        disposeContainer();
        _status = null;

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
                Log.info("Failed to find proxy settings in Windows registry [error=" + t + "].");
            }
        }

        // otherwise look for and read our proxy.txt file
        File pfile = _app.getLocalPath("proxy.txt");
        if (pfile.exists()) {
            try {
                HashMap pconf = ConfigUtil.parseConfig(pfile, false);
                setProxyProperties((String)pconf.get("host"), (String)pconf.get("port"));
                return true;
            } catch (IOException ioe) {
                Log.warning("Failed to read '" + pfile + "': " + ioe);
            }
        }

        // otherwise see if we actually need a proxy; first we have to initialize our application
        // to get some sort of interface configuration and the appbase URL
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
            HttpURLConnection ucon = (HttpURLConnection)rurl.openConnection();
            ucon.setRequestMethod("HEAD");
            ucon.connect();

            // make sure we got a satisfactory response code
            if (ucon.getResponseCode() != HttpURLConnection.HTTP_OK) {
                Log.warning("Got a non-200 response but assuming we're OK because we got " +
                            "something... [url=" + rurl + ", rsp=" + ucon.getResponseCode() + "].");
            }

            // we got through, so we appear not to require a proxy; make a blank proxy config and
            // get on gettin' down
            Log.info("No proxy appears to be needed.");
            try {
                pfile.createNewFile();
            } catch (IOException ioe) {
                Log.warning("Failed to create blank proxy file '" + pfile + "': " + ioe);
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
     * Does the actual application validation, update and launching business.
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
                // if we aren't running in a JVM that meets our version requirements, either
                // complain or attempt to download and install the appropriate version
                if (!_app.haveValidJavaVersion()) {
                    // download and install the necessary version of java, then loop back again and
                    // reverify everything; if we can't download java; we'll throw an exception
                    Log.info("Attempting to update Java VM...");
                    updateJava();
                    continue;
                }

                // make sure we have the desired version and that the metadata files are valid...
                setStatus("m.validating", -1, -1L, false);
                if (_app.verifyMetadata(this)) {
                    Log.info("Application requires update.");
                    update();
                    // loop back again and reverify the metadata
                    continue;
                }

                // now verify our resources...
                setStatus("m.validating", -1, -1L, false);
                List<Resource> failures = _app.verifyResources(_progobs);
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
                    msg = MessageUtil.compose(
                        "m.missing_resource", MessageUtil.taint(msg), _ifc.installError);
                } else {
                    msg = MessageUtil.compose(
                        "m.init_error", msg, MessageUtil.taint(msg), _ifc.installError);
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

        updateStatus("m.downloading_java");
        ArrayList<Resource> list = new ArrayList<Resource>();
        list.add(vmjar);
        download(list);

        updateStatus("m.unpacking_java");
        if (!vmjar.unpack()) {
            throw new IOException("m.java_unpack_failed");
        }
        vmjar.markAsValid();

        // Sun, why dost thou spite me? Java doesn't know anything about file permissions (and by
        // extension then, neither does Jar), so on Joonix we have to hackily make java_vm/bin/java
        // executable by execing chmod; a pox on their children!
        if (RunAnywhere.isLinux()) {
            String vmbin = LaunchUtil.LOCAL_JAVA_DIR + File.separator + "bin" +
                File.separator + "java";
            String cmd = "chmod a+rx " + _app.getLocalPath(vmbin);
            try {
                Log.info("Please smack a Java engineer. Running: " + cmd);
                Runtime.getRuntime().exec(cmd);
            } catch (Exception e) {
                Log.warning("Failed to mark VM binary as executable [cmd=" + cmd +
                            ", error=" + e + "].");
                // we should do something like tell the user or something but fucking fuck
            }
        }

        // lastly regenerate the .jsa dump file that helps Java to start up faster
        String vmpath = LaunchUtil.getJVMPath(_app.getLocalPath(""));
        try {
            Log.info("Regenerating classes.jsa for " + vmpath + "...");
            Runtime.getRuntime().exec(vmpath + " -Xshare:dump");
        } catch (Exception e) {
            Log.warning("Failed to regenerate .jsa dum file [error=" + e + "].");
        }
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
            ArrayList<Resource> list = new ArrayList<Resource>();
            list.add(patch);

            // add the auxiliary group patch files for activated groups
            for (String auxgroup : _app.getAuxGroups()) {
                if (_app.isAuxGroupActive(auxgroup)) {
                    patch = _app.getPatchResource(auxgroup);
                    if (patch != null) {
                        list.add(patch);
                    }
                }
            }

            // download the patch files...
            download(list);

            // and apply them...
            updateStatus("m.patching");
            for (Resource prsrc : list) {
                try {
                    Patcher patcher = new Patcher();
                    patcher.patch(prsrc.getLocal().getParentFile(), prsrc.getLocal(), _progobs);
                } catch (Exception e) {
                    Log.warning("Failed to apply patch [prsrc=" + prsrc + "].");
                    Log.logStackTrace(e);
                }

                // clean up the patch file
                if (!prsrc.getLocal().delete()) {
                    Log.warning("Failed to delete '" + prsrc + "'.");
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
                updateStatus(MessageUtil.tcompose("m.failure", e.getMessage()));
                Log.warning("Download failed [rsrc=" + rsrc + "].");
                Log.logStackTrace(e);
                synchronized (lock) {
                    lock.notify();
                }
            }
        };

        // assume we're going to use an HTTP downloader
        Downloader dl = new HTTPDownloader(resources, obs);

        // if torrent downloading is enabled and we are downloading the right set of resources (a
        // single patch file or the entire app from scratch), then use a torrent downloader
        // instead.  Because many of our installers also bundle background.png, and might bundle
        // more required files, we need to allow a 'fudge factor' threshhold for determining at
        // which point it is faster to torrent, and at which point we should use HTTP.
        if (_app.getUseTorrent()) {
            int verifiedResources = _app.getAllResources().size() - resources.size();
            if (verifiedResources <= MAX_TORRENT_VERIFIED_RESOURCES) {
                ArrayList<Resource> full = new ArrayList<Resource>();
                full.add(_app.getFullResource());
                full.addAll(resources);
                dl = new TorrentDownloader(full, obs);
            } else if (resources.size() == 1 && resources.get(0).getPath().startsWith("patch")) {
                dl = new TorrentDownloader(resources, obs);
            }
        }

        // start the download and wait for it to complete
        dl.start();
        synchronized (lock) {
            try {
                lock.wait();
            } catch (InterruptedException ie) {
                Log.warning("Waitus interruptus " + ie + ".");
            }
        }
    }

    /**
     * Called to launch the application if everything is determined to be ready to go.
     */
    protected void launch ()
    {
        setStatus("m.launching", 100, -1L, false);

        try {
            if (invokeDirect()) {
                _app.invokeDirect(getApplet());

            } else {
                Process proc = _app.createProcess();

                // on Windows 98 and ME we need to stick around and read the output of stderr lest
                // the process fill its output buffer and choke, yay!
                final InputStream stderr = proc.getErrorStream();
                if (LaunchUtil.mustMonitorChildren()) {
                    // close our window if it's around
                    disposeContainer();
                    _status = null;
                    BufferedReader reader = new BufferedReader(new InputStreamReader(stderr));
                    while (reader.readLine() != null) {
                        // nothing doing!
                    }
                    Log.info("Process exited: " + proc.waitFor());

                } else {
                    // spawn a daemon thread that will catch the early bits of stderr in case the
                    // launch fails
                    Thread t = new Thread() {
                        public void run () {
                            try {
                                BufferedReader reader =
                                    new BufferedReader(new InputStreamReader(stderr));
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    Log.warning(line);
                                }
                            } catch (IOException ioe) {
                                // oh well
                            }
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
            exit(0);

        } catch (Exception e) {
            Log.logStackTrace(e);
        }
    }

    /**
     * Creates our user interface, which we avoid doing unless we actually have to update
     * something.
     */
    protected void createInterface (boolean force)
    {
        if (_container != null && !force) {
            return;
        }

        EventQueue.invokeLater(new Runnable() {
            public void run () {
                if (_status == null) {
                    _container = createContainer(); 
                    _status = new StatusPanel(_msgs);
                    _container.add(_status, BorderLayout.CENTER);
                }
                _status.init(_ifc, getBackgroundImage(), getProgressImage());
                showContainer();
            }
        });
    }

    protected Image getBackgroundImage ()
    {
        return loadImage(_ifc.backgroundImage);
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

    protected void setStatus (
        final String message, final int percent, final long remaining, boolean createUI)
    {
        if (_status == null && createUI) {
            createInterface(false);
        }

        EventQueue.invokeLater(new Runnable() {
            public void run () {
                if (_status == null) {
                    Log.info("Dropping status '" + message + "'.");
                    return;
                }
                if (message != null) {
                    _status.setStatus(message);
                }
                if (percent >= 0) {
                    _status.setProgress(percent, remaining);
                }
            }
        });
    }

    /**
     * Load the image at the path.  Before trying the exact path/file specified
     *  we will look to see if we can find a localized version by sticking a
     *  _<language> in front of the "." in the filename.
     */
    protected BufferedImage loadImage (String path)
    {
        String localeStr = Locale.getDefault().getLanguage();

        if (StringUtil.isBlank(path)) {
            return null;
        }
        
        File imgpath = null;
        try {
            // First try for a localized image.
            imgpath = _app.getLocalPath(StringUtil.replace(path, ".", "_" + localeStr + "."));
            return ImageIO.read(imgpath);
        } catch (IOException ioe) {
            // No biggie, we'll try the generic one.
        }

        // If that didn't work, try a generic one.
        try {
            imgpath = _app.getLocalPath(path);
            return ImageIO.read(imgpath);
        } catch (IOException ioe2) {
            Log.warning("Failed to load image [path=" + imgpath + ", error=" + ioe2 + "].");
            return null;
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
        return false;
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
     * Requests that Getdown exit. In applet mode this does nothing.
     */
    protected abstract void exit (int exitCode);

    /** Used to pass progress on to our user interface. */
    protected ProgressObserver _progobs = new ProgressObserver() {
        public void progress (final int percent) {
            setStatus(null, percent, -1L, false);
        }
    };

    protected Application _app;
    protected Application.UpdateInterface _ifc = new Application.UpdateInterface();

    protected ResourceBundle _msgs;
    protected Container _container;
    protected StatusPanel _status;
    protected AbortPanel _abort;

    protected boolean _dead;
    protected long _startup;

    /** The maximum number of resources that can be already present for bittorrent to be used. */
    protected static final int MAX_TORRENT_VERIFIED_RESOURCES = 1;

    protected static final int MAX_LOOPS = 5;
    protected static final long MIN_EXIST_TIME = 5000L;
    protected static final String PROXY_REGISTRY =
        "Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings";
}
