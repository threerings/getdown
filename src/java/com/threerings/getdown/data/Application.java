//
// $Id$
//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2010 Three Rings Design, Inc.
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
package com.threerings.getdown.data;

import java.awt.Color;
import java.awt.Rectangle;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.security.AllPermission;
import java.security.CodeSource;
import java.security.GeneralSecurityException;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.Signature;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JApplet;

import org.apache.commons.codec.binary.Base64;

import com.samskivert.io.StreamUtil;
import com.samskivert.text.MessageUtil;
import com.samskivert.util.ArrayIntSet;
import com.samskivert.util.RandomUtil;
import com.samskivert.util.RunAnywhere;
import com.samskivert.util.StringUtil;

import com.threerings.getdown.launcher.RotatingBackgrounds;
import com.threerings.getdown.util.ConfigUtil;
import com.threerings.getdown.util.FileUtil;
import com.threerings.getdown.util.LaunchUtil;
import com.threerings.getdown.util.MetaProgressObserver;
import com.threerings.getdown.util.ProgressObserver;

import static com.threerings.getdown.Log.log;

/**
 * Parses and provide access to the information contained in the <code>getdown.txt</code>
 * configuration file.
 */
public class Application
{
    /** The name of our configuration file. */
    public static final String CONFIG_FILE = "getdown.txt";

    /** The name of our target version file. */
    public static final String VERSION_FILE = "version.txt";

    /** System properties that are prefixed with this string will be passed through to our
     * application (minus this prefix). */
    public static final String PROP_PASSTHROUGH_PREFIX = "app.";

    /** Suffix used for control file signatures. */
    public static final String SIGNATURE_SUFFIX = ".sig";

    /** Used to communicate information about the UI displayed when updating the application. */
    public static class UpdateInterface
    {
        /** The human readable name of this application. */
        public String name;

        /** Background image specifiers for {@link RotatingBackgrounds}. */
        public String[] rotatingBackgrounds;

        /** The error background image for {@link RotatingBackgrounds}. */
        public String errorBackground;

        /** The path (relative to the appdir) to a single background image. */
        public String backgroundImage;

        /** The path (relative to the appdir) to the progress bar image. */
        public String progressImage;

        /** The dimensions of the progress bar. */
        public Rectangle progress = new Rectangle(5, 5, 300, 15);

        /** The color of the progress text. */
        public Color progressText = Color.black;

        /** The color of the progress bar. */
        public Color progressBar = new Color(0x6699CC);

        /** The dimensions of the status display. */
        public Rectangle status = new Rectangle(5, 25, 500, 100);

        /** The color of the status text. */
        public Color statusText = Color.black;

        /** The color of the text shadow. */
        public Color textShadow;

        /** Where to point the user for help with install errors. */
        public String installError;

        /** Generates a string representation of this instance. */
        @Override
        public String toString ()
        {
            return "[name=" + name + ", bg=" + backgroundImage + ", pi=" + progressImage +
                ", prect=" + progress + ", pt=" + progressText + ", pb=" + progressBar +
                ", srect=" + status + ", st=" + statusText + ", shadow=" + textShadow +
                ", err=" + installError + "]";
        }
    }

    /**
     * Used by {@link #verifyMetadata} to communicate status in circumstances where it needs to
     * take network actions.
     */
    public static interface StatusDisplay
    {
        /** Requests that the specified status message be displayed. */
        public void updateStatus (String message);
    }

    /**
     * Creates an application instance with no signers.
     *
     * @see #Application(File, String, Object[])
     */
    public Application (File appdir, String appid)
    {
        this(appdir, appid, null, null, null);
    }

    /**
     * Creates an application instance which records the location of the <code>getdown.txt</code>
     * configuration file from the supplied application directory.
     *
     * @param appid usually null but a string identifier if a secondary application is desired to
     * be launched. That application will use <code>appid.class</code> and
     * <code>appid.apparg</code> to configure itself but all other parameters will be the same as
     * the primary application.
     * @param signers an array of possible signers of this application. Used to verify the digest.
     * @param jvmargs arguments to pass on to launched jvms
     * @param appargs arguments to pass on to launched application
     */
    public Application (
        File appdir, String appid, Object[] signers, String[] jvmargs, String[] appargs)
    {
        _appdir = appdir;
        _appid = appid;
        _signers = signers;
        _config = getLocalPath(CONFIG_FILE);
        if (jvmargs != null) {
            _baseJvmArgs = jvmargs;
        }
        if (appargs != null) {
            _baseAppArgs = appargs;
        }
    }

    /**
     * Indicates whether or not we support downloading of our resources using the Bittorrent
     * protocol.
     */
    public boolean getUseTorrent ()
    {
        return _useTorrent;
    }

    /**
     * Returns a resource that refers to the application configuration file itself.
     */
    public Resource getConfigResource ()
    {
        try {
            return createResource(CONFIG_FILE, false);
        } catch (Exception e) {
            throw new RuntimeException("Invalid appbase '" + _vappbase + "'.");
        }
    }

    /**
     * Returns a list of the code {@link Resource} objects used by this application.
     */
    public List<Resource> getCodeResources ()
    {
        return _codes;
    }

    /**
     * Returns a list of the non-code {@link Resource} objects used by this application.
     */
    public List<Resource> getResources ()
    {
        return _resources;
    }

    /**
     * Returns a list of all the {@link Resource} objects used by this application.
     */
    public List<Resource> getAllResources ()
    {
        List<Resource> allResources = new ArrayList<Resource>();
        allResources.addAll(getCodeResources());
        allResources.addAll(getActiveResources());
        return allResources;
    }

    /**
     * Returns a list of all auxiliary resource groups defined by the application. An auxiliary
     * resource group is a collection of resource files that are not downloaded unless a group
     * token file is present in the application directory.
     */
    public List<String> getAuxGroups ()
    {
        return _auxgroups;
    }

    /**
     * Returns true if the specified auxgroup has been "activated", false if not. Non-activated
     * groups should be ignored, activated groups should be downloaded and patched along with the
     * main resources.
     */
    public boolean isAuxGroupActive (String auxgroup)
    {
        Boolean active = _auxactive.get(auxgroup);
        if (active == null) {
            // TODO: compare the contents with the MD5 hash of the auxgroup name and the client's
            // machine ident
            active = getLocalPath(auxgroup + ".dat").exists();
            _auxactive.put(auxgroup, active);
        }
        return active;
    }

    /**
     * Returns a list of the non-code {@link Resource} objects included in the specified auxiliary
     * resource group. If the group does not exist or has no resources, an empty list will be
     * returned.
     */
    public List<Resource> getResources (String group)
    {
        ArrayList<Resource> auxrsrcs = _auxrsrcs.get(group);
        return (auxrsrcs == null) ? new ArrayList<Resource>() : auxrsrcs;
    }

    /**
     * Returns all non-code resources and all resources from active auxiliary resource groups.
     */
    public List<Resource> getActiveResources ()
    {
        ArrayList<Resource> rsrcs = new ArrayList<Resource>();
        rsrcs.addAll(getResources());
        for (String auxgroup : getAuxGroups()) {
            if (isAuxGroupActive(auxgroup)) {
                rsrcs.addAll(getResources(auxgroup));
            }
        }
        return rsrcs;
    }

    /**
     * Returns a resource that can be used to download a patch file that will bring this
     * application from its current version to the target version.
     *
     * @param auxgroup the auxiliary resource group for which a patch resource is desired or null
     * for the main application patch resource.
     */
    public Resource getPatchResource (String auxgroup)
    {
        if (_targetVersion <= _version) {
            log.warning("Requested patch resource for up-to-date or non-versioned application " +
                        "[cvers=" + _version + ", tvers=" + _targetVersion + "].");
            return null;
        }

        String infix = (auxgroup == null) ? "" : ("-" + auxgroup);
        String pfile = "patch" + infix + _version + ".dat";
        try {
            URL remote = new URL(createVAppBase(_targetVersion), pfile);
            return new Resource(pfile, remote, getLocalPath(pfile), false);
        } catch (Exception e) {
            log.warning("Failed to create patch resource path [pfile=" + pfile +
                        ", appbase=" + _appbase + ", tvers=" + _targetVersion +
                        ", error=" + e + "].");
            return null;
        }
    }

    /**
     * Returns a resource for a zip file containing a Java VM that can be downloaded to use in
     * place of the installed VM (in the case where the VM that launched Getdown does not meet the
     * application's version requirements) or null if no VM is available for this platform.
     */
    public Resource getJavaVMResource ()
    {
        if (StringUtil.isBlank(_javaLocation)) {
            return null;
        }

        String vmfile = LaunchUtil.LOCAL_JAVA_DIR + ".jar";
        try {
            URL remote = new URL(createVAppBase(_targetVersion), _javaLocation);
            return new Resource(vmfile, remote, getLocalPath(vmfile), true);
        } catch (Exception e) {
            log.warning("Failed to create VM resource [vmfile=" + vmfile + ", appbase=" + _appbase +
                        ", tvers=" + _targetVersion + ", javaloc=" + _javaLocation +
                        ", error=" + e + "].");
            return null;
        }
    }

    /**
     * Returns a resource that can be used to download an archive containing all files belonging to
     * the application.
     */
    public Resource getFullResource ()
    {
        String file = "full";
        try {
            URL remote = new URL(createVAppBase(_targetVersion), file);
            return new Resource(file, remote, getLocalPath(file), false);
        } catch (Exception e) {
            log.warning("Failed to create full resource path [file=" + file +
                        ", appbase=" + _appbase + ", tvers=" + _targetVersion +
                        ", error=" + e + "].");
            return null;
        }
    }

    /**
     * Returns the URL to use to report an initial download event. Returns null if no tracking
     * start URL was configured for this application.
     *
     * @param event the event to be reported: start, jvm_start, jvm_complete, complete.
     */
    public URL getTrackingURL (String event)
    {
        try {
            String suffix = _trackingURLSuffix == null ? "" : _trackingURLSuffix;
            String ga = getGATrackingCode();
            return _trackingURL == null ?
                null : new URL(_trackingURL + event + suffix + ga);
        } catch (MalformedURLException mue) {
            log.warning("Invalid tracking URL [path=" + _trackingURL + ", event=" + event +
                        ", error=" + mue + "].");
            return null;
        }
    }

    /**
     * Returns the URL to request to report that we have reached the specified percentage of our
     * initial download. Returns null if no tracking request was configured for the specified
     * percentage.
     */
    public URL getTrackingProgressURL (int percent)
    {
        if (_trackingPcts == null || !_trackingPcts.contains(percent)) {
            return null;
        }
        return getTrackingURL("pct" + percent);
    }

    /**
     * Returns the name of our tracking cookie or null if it was not set.
     */
    public String getTrackingCookieName ()
    {
        return _trackingCookieName;
    }

    /**
     * Returns the name of our tracking cookie system property or null if it was not set.
     */
    public String getTrackingCookieProperty ()
    {
        return _trackingCookieProperty;
    }

    /**
     * Instructs the application to parse its <code>getdown.txt</code> configuration and prepare
     * itself for operation. The application base URL will be parsed first so that if there are
     * errors discovered later, the caller can use the application base to download a new
     * <code>config.txt</code> file and try again.
     *
     * @return a configured UpdateInterface instance that will be used to configure the update UI.
     *
     * @exception IOException thrown if there is an error reading the file or an error encountered
     * during its parsing.
     */
    public UpdateInterface init (boolean checkPlatform)
        throws IOException
    {
        // parse our configuration file
        HashMap<String,Object> cdata = null;
        try {
            cdata = ConfigUtil.parseConfig(_config, checkPlatform);
        } catch (FileNotFoundException fnfe) {
            // thanks to funny windows bullshit, we have to do this backup file fiddling in case we
            // got screwed while updating our very critical getdown config file
            File cbackup = getLocalPath(CONFIG_FILE + "_old");
            if (cbackup.exists()) {
                cdata = ConfigUtil.parseConfig(cbackup, checkPlatform);
            } else {
                throw fnfe;
            }
        }

        // first determine our application base, this way if anything goes wrong later in the
        // process, our caller can use the appbase to download a new configuration file
        _appbase = (String)cdata.get("appbase");
        if (_appbase == null) {
            throw new IOException("m.missing_appbase");
        }
        // make sure there's a trailing slash
        if (!_appbase.endsWith("/")) {
            _appbase = _appbase + "/";
        }

        // extract our version information
        String vstr = (String)cdata.get("version");
        if (vstr != null) {
            try {
                _version = Long.parseLong(vstr);
            } catch (Exception e) {
                String err = MessageUtil.tcompose("m.invalid_version", vstr);
                throw (IOException) new IOException(err).initCause(e);
            }
        }

        // if we are a versioned deployment, create a versioned appbase
        try {
            if (_version < 0) {
                _vappbase = new URL(_appbase);
            } else {
                _vappbase = createVAppBase(_version);
            }
        } catch (MalformedURLException mue) {
            String err = MessageUtil.tcompose("m.invalid_appbase", _appbase);
            throw (IOException) new IOException(err).initCause(mue);
        }

        String prefix = (_appid == null) ? "" : (_appid + ".");

        // determine our application class name
        _class = (String)cdata.get(prefix + "class");
        if (_class == null) {
            throw new IOException("m.missing_class");
        }

        // check to see if we require a particular JVM version and have a supplied JVM
        vstr = (String)cdata.get("java_version");
        if (vstr != null) {
            try {
                _javaVersion = Integer.parseInt(vstr);
            } catch (Exception e) {
                String err = MessageUtil.tcompose("m.invalid_java_version", vstr);
                throw (IOException) new IOException(err).initCause(e);
            }
        }

        // this is a little weird, but when we're run from the digester, we see a String[] which
        // contains java locations for all platforms which we can't grok, but the digester doesn't
        // need to know about that; when we're run in a real application there will be only one!
        Object javaloc = cdata.get("java_location");
        if (javaloc instanceof String) {
            _javaLocation = (String)javaloc;
        }

        // determine whether we have any tracking configuration
        _trackingURL = (String)cdata.get("tracking_url");

        // check for tracking progress percent configuration
        String trackPcts = (String)cdata.get("tracking_percents");
        if (!StringUtil.isBlank(trackPcts)) {
            _trackingPcts = new ArrayIntSet(StringUtil.parseIntArray(trackPcts));
        } else if (!StringUtil.isBlank(_trackingURL)) {
            _trackingPcts = new ArrayIntSet(new int[] { 50 });
        }

        // Check for tracking cookie configuration
        _trackingCookieName = (String)cdata.get("tracking_cookie_name");
        _trackingCookieProperty = (String)cdata.get("tracking_cookie_property");

        // Some app may need an extra suffix added to the tracking URL
        _trackingURLSuffix = (String)cdata.get("tracking_url_suffix");

        // Some app may need to generate google analytics code
        _trackingGAHash = (String)cdata.get("tracking_ga_hash");

        // clear our arrays as we may be reinitializing
        _codes.clear();
        _resources.clear();
        _auxgroups.clear();
        _auxrsrcs.clear();
        _jvmargs.clear();
        _appargs.clear();

        // parse our code resources
        if (ConfigUtil.getMultiValue(cdata, "code") == null) {
            throw new IOException("m.missing_code");
        }
        parseResources(cdata, "code", false, _codes);

        // parse our non-code resources
        parseResources(cdata, "resource", false, _resources);
        parseResources(cdata, "uresource", true, _resources);

        // parse our auxiliary resource groups
        for (String auxgroup : parseList(cdata, "auxgroups")) {
            ArrayList<Resource> rsrcs = new ArrayList<Resource>();
            parseResources(cdata, auxgroup + ".resource", false, rsrcs);
            parseResources(cdata, auxgroup + ".uresource", true, rsrcs);
            _auxrsrcs.put(auxgroup, rsrcs);
            _auxgroups.add(auxgroup);
        }

        // transfer our JVM arguments
        String[] jvmargs = ConfigUtil.getMultiValue(cdata, "jvmarg");
        if (jvmargs != null) {
            for (String jvmarg : jvmargs) {
                _jvmargs.add(jvmarg);
            }
        }

        // Add the launch specific JVM arguments
        for (String arg : _baseJvmArgs) {
            _jvmargs.add(arg);
        }

        // transfer our application arguments
        String[] appargs = ConfigUtil.getMultiValue(cdata, prefix + "apparg");
        if (appargs != null) {
            for (String apparg : appargs) {
                _appargs.add(apparg);
            }
        }

        // add the launch specific application arguments
        for (String arg : _baseAppArgs) {
            _appargs.add(arg);
        }

        // look for custom arguments
        fillAssignmentListFromPairs("extra.txt", _jvmargs);

        // determine whether or not we should be using bit torrent
        _useTorrent = (cdata.get("torrent") != null) || (System.getProperty("torrent") != null);

        // determine whether we want to allow offline operation (defaults to false)
        _allowOffline = Boolean.parseBoolean((String)cdata.get("allow_offline"));

        // look for a debug.txt file which causes us to run in java.exe on Windows so that we can
        // obtain a thread dump of the running JVM
        _windebug = getLocalPath("debug.txt").exists();

        // parse and return our application config
        UpdateInterface ui = new UpdateInterface();
        _name = ui.name = (String)cdata.get("ui.name");
        ui.progress = parseRect(cdata, "ui.progress", ui.progress);
        ui.progressText = parseColor(cdata, "ui.progress_text", ui.progressText);
        ui.progressBar = parseColor(cdata, "ui.progress_bar", ui.progressBar);
        ui.status = parseRect(cdata, "ui.status", ui.status);
        ui.statusText = parseColor(cdata, "ui.status_text", ui.statusText);
        ui.textShadow = parseColor(cdata, "ui.text_shadow", ui.textShadow, false);
        ui.backgroundImage = (String)cdata.get("ui.background_image");
        if (ui.backgroundImage == null) { // support legacy format
            ui.backgroundImage = (String)cdata.get("ui.background");
        }
        ui.progressImage = (String)cdata.get("ui.progress_image");
        ui.rotatingBackgrounds = ConfigUtil.getMultiValue(cdata, "ui.rotating_background");
        ui.errorBackground = (String)cdata.get("ui.error_background");
        _dockIconPath = (String)cdata.get("ui.mac_dock_icon");
        if (_dockIconPath == null) {
            _dockIconPath = "../desktop.icns"; // use a sensible default
        }

        // On an installation error, where do we point the user.
        ui.installError = (String)cdata.get("ui.install_error");
        if (StringUtil.isBlank(ui.installError)) {
            ui.installError = "m.default_install_error";
        } else {
            ui.installError = MessageUtil.taint(ui.installError);
        }

        return ui;
    }

    /**
     * Adds strings of the form pair0=pair1 to collector for each pair parsed out of pairLocation.
     */
    protected void fillAssignmentListFromPairs (String pairLocation, List<String> collector)
    {
        File pairFile = getLocalPath(pairLocation);
        if (pairFile.exists()) {
            try {
                List<String[]> args = ConfigUtil.parsePairs(pairFile, false);
                for (String[] pair : args) {
                    collector.add(pair[0] + "=" + pair[1]);
                }
            } catch (Throwable t) {
                log.warning("Failed to parse '" + pairFile + "': " + t);
            }
        }
    }

    /**
     * Returns a URL from which the specified path can be fetched. Our application base URL is
     * properly versioned and combined with the supplied path.
     */
    public URL getRemoteURL (String path)
        throws MalformedURLException
    {
        return new URL(_vappbase, path);
    }

    /**
     * Returns the local path to the specified resource.
     */
    public File getLocalPath (String path)
    {
        return new File(_appdir, path);
    }

    /**
     * Returns true if we either have no version requirement, are running in a JVM that meets our
     * version requirements or have what appears to be a version of the JVM that meets our
     * requirements.
     */
    public boolean haveValidJavaVersion ()
    {
        // if we're doing no version checking, then yay!
        if (_javaVersion <= 0) {
            return true;
        }

        // if we have a fully unpacked VM assume it is the right version (TODO: don't)
        Resource vmjar = getJavaVMResource();
        if (vmjar != null && vmjar.isMarkedValid()) {
            return true;
        }

        // parse the version out of the java.version system property
        String verstr = System.getProperty("java.version");
        Matcher m = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)(_\\d+)?.*").matcher(verstr);
        if (!m.matches()) {
            // if we can't parse the java version we're in weird land and should probably just try
            // our luck with what we've got rather than try to download a new jvm
            log.warning("Unable to parse VM version, hoping for the best [version=" + verstr +
                        ", needed=" + _javaVersion + "].");
            return true;
        }

        int one = Integer.parseInt(m.group(1)); // will there ever be a two?
        int major = Integer.parseInt(m.group(2));
        int minor = Integer.parseInt(m.group(3));
        int patch = m.group(4) == null ? 0 : Integer.parseInt(m.group(4).substring(1));
        int version = patch + 100 * (minor + 100 * (major + 100 * one));
        return version >= _javaVersion;
    }

    /**
     * Attempts to redownload the <code>getdown.txt</code> file based on information parsed from a
     * previous call to {@link #init}.
     */
    public void attemptRecovery (StatusDisplay status)
        throws IOException
    {
        status.updateStatus("m.updating_metadata");
        downloadConfigFile();
    }

    /**
     * Downloads and replaces the <code>getdown.txt</code> and <code>digest.txt</code> files with
     * those for the target version of our application.
     */
    public void updateMetadata ()
        throws IOException
    {
        try {
            // update our versioned application base with the target version
            _vappbase = createVAppBase(_targetVersion);
        } catch (MalformedURLException mue) {
            String err = MessageUtil.tcompose("m.invalid_appbase", _appbase);
            throw (IOException) new IOException(err).initCause(mue);
        }

        try {
            // now re-download our control files; we download the digest first so that if it fails,
            // our config file will still reference the old version and re-running the updater will
            // start the whole process over again
            downloadDigestFile();
            downloadConfigFile();

        } catch (IOException ex) {
            // if we are allowing offline execution, we want to allow the application to run in its
            // current form rather than aborting the entire process; to do this, we delete the
            // version.txt file and "trick" Getdown into thinking that it just needs to validate
            // the application as is; next time the app runs when connected to the internet, it
            // will have to rediscover that it needs updating and reattempt to update itself
            if (_allowOffline) {
                log.warning("Failed to update digest files.  Attempting offline operaton.", ex);
                if (!getLocalPath(VERSION_FILE).delete()) {
                    log.warning("Deleting version.txt failed.  This probably isn't going to work.");
                }
            } else {
                throw ex;
            }
        }
    }

    /**
     * Invokes the process associated with this application definition.
     */
    public Process createProcess ()
        throws IOException
    {
        // create our classpath
        StringBuilder cpbuf = new StringBuilder();
        for (Resource rsrc : _codes) {
            if (cpbuf.length() > 0) {
                cpbuf.append(File.pathSeparator);
            }
            cpbuf.append(rsrc.getLocal().getAbsolutePath());
        }

        ArrayList<String> args = new ArrayList<String>();

        // reconstruct the path to the JVM
        args.add(LaunchUtil.getJVMPath(_appdir, _windebug));

        // add the classpath arguments
        args.add("-classpath");
        args.add(cpbuf.toString());

        // we love our Mac users, so we do nice things to preserve our application identity
        if (RunAnywhere.isMacOS()) {
            args.add("-Xdock:icon=" + getLocalPath(_dockIconPath).getAbsolutePath());
            args.add("-Xdock:name=" + _name);
        }

        // pass along our proxy settings
        String proxyHost;
        if ((proxyHost = System.getProperty("http.proxyHost")) != null) {
            args.add("-Dhttp.proxyHost=" + proxyHost);
            args.add("-Dhttp.proxyPort=" + System.getProperty("http.proxyPort"));
        }

        // pass along any pass-through arguments
        for (Map.Entry<Object, Object> entry : System.getProperties().entrySet()) {
            String key = (String)entry.getKey();
            if (key.startsWith(PROP_PASSTHROUGH_PREFIX)) {
                key = key.substring(PROP_PASSTHROUGH_PREFIX.length());
                args.add("-D" + key + "=" + entry.getValue());
            }
        }

        // add the JVM arguments
        for (String string : _jvmargs) {
            args.add(processArg(string));
        }

        // add the application class name
        args.add(_class);

        // finally add the application arguments
        for (String string : _appargs) {
            args.add(processArg(string));
        }


        String[] envp = createEnvironment();
        String[] sargs = new String[args.size()];
        args.toArray(sargs);
        log.info("Running " + StringUtil.join(sargs, "\n  "));

        return Runtime.getRuntime().exec(sargs, envp);
    }

    /**
     * If the application provided environment variables, combine those with the current
     * environment and return that in a style usable for {@link Runtime#exec(String, String[])}.
     * If the application didn't provide any environment variables, null is returned to just use
     * the existing environment. If the application provided environment variables and this is
     * running on Java 1.4 which can't find the existing environment, null is returned so the
     * existing environment is used and things have a chance of running.
     */
    protected String[] createEnvironment ()
    {
        List<String> envvar = new ArrayList<String>();
        fillAssignmentListFromPairs("env.txt", envvar);
        if (envvar.isEmpty()) {
            log.info("Didn't find any custom environment variables, not setting any.");
            return null;
        }
        Map<String, String> existing;
        try {
            existing = System.getenv();
        } catch (NoSuchMethodError nsme) {
            log.warning("Unable to access the existing environment, most likely due to being on "
                + "Java 1.4.  Not setting application specific environment.", "applicationEnv",
                envvar, nsme);
            return null;
        }
        List<String> envAssignments = new ArrayList<String>();
        for (String assignment : envvar) {
            envAssignments.add(processArg(assignment));
        }
        for (Entry<String, String> environmentEntry : existing.entrySet()) {
            envAssignments.add(environmentEntry.getKey() + "=" + environmentEntry.getValue());
        }
        String[] envp = envAssignments.toArray(new String[envAssignments.size()]);
        log.info("Environment " + StringUtil.join(envp, "\n "));
        return envp;
    }

    /**
     * Runs this application directly in the current VM.
     */
    public void invokeDirect (JApplet applet)
    {
        // create a custom class loader
        ArrayList<URL> jars = new ArrayList<URL>();
        for (Resource rsrc : _codes) {
            try {
                jars.add(new URL("file", "", rsrc.getLocal().getAbsolutePath()));
            } catch (Exception e) {
                e.printStackTrace(System.err);
            }
        }
        URLClassLoader loader = new URLClassLoader(
            jars.toArray(new URL[jars.size()]),
            ClassLoader.getSystemClassLoader()) {
            @Override protected PermissionCollection getPermissions (CodeSource code) {
                Permissions perms = new Permissions();
                perms.add(new AllPermission());
                return perms;
            }
        };

        // configure any system properties that we can
        for (String jvmarg : _jvmargs) {
            if (jvmarg.startsWith("-D")) {
                jvmarg = processArg(jvmarg.substring(2));
                int eqidx = jvmarg.indexOf("=");
                if (eqidx == -1) {
                    log.warning("Bogus system property: '" + jvmarg + "'?");
                } else {
                    System.setProperty(jvmarg.substring(0, eqidx), jvmarg.substring(eqidx+1));
                }
            }
        }

        // pass along any pass-through arguments
        Map<String, String> passProps = new HashMap<String, String>();
        for (Map.Entry<Object, Object> entry : System.getProperties().entrySet()) {
            String key = (String)entry.getKey();
            if (key.startsWith(PROP_PASSTHROUGH_PREFIX)) {
                key = key.substring(PROP_PASSTHROUGH_PREFIX.length());
                passProps.put(key, (String)entry.getValue());
            }
        }
        // we can't set these in the above loop lest we get a ConcurrentModificationException
        for (Map.Entry<String, String> entry : passProps.entrySet()) {
            System.setProperty(entry.getKey(), entry.getValue());
        }

        // make a note that we're running in "applet" mode
        System.setProperty("applet", "true");

        try {
            Class<?> appclass = loader.loadClass(_class);
            String[] args = _appargs.toArray(new String[_appargs.size()]);
            Method main;
            try {
                // first see if the class has a special applet-aware main
                main = appclass.getMethod("main", JApplet.class, SA_PROTO.getClass());
                main.invoke(null, new Object[] { applet, args });
            } catch (NoSuchMethodException nsme) {
                main = appclass.getMethod("main", SA_PROTO.getClass());
                main.invoke(null, new Object[] { args });
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
    }

    /** Replaces the application directory and version in any argument. */
    protected String processArg (String arg)
    {
        arg = StringUtil.replace(arg, "%APPDIR%", _appdir.getAbsolutePath());
        arg = StringUtil.replace(arg, "%VERSION%", String.valueOf(_version));
        return arg;
    }

    /**
     * Loads the <code>digest.txt</code> file and verifies the contents of both that file and the
     * <code>getdown.text</code> file. Then it loads the <code>version.txt</code> and decides
     * whether or not the application needs to be updated or whether we can proceed to verification
     * and execution.
     *
     * @return true if the application needs to be updated, false if it is up to date and can be
     * verified and executed.
     *
     * @exception IOException thrown if we encounter an unrecoverable error while verifying the
     * metadata.
     */
    public boolean verifyMetadata (StatusDisplay status)
        throws IOException
    {
        log.info("Verifying application: " + _vappbase);
        log.info("Version: " + _version);
        log.info("Class: " + _class);
//         log.info("Code: " +
//                  StringUtil.toString(getCodeResources().iterator()));
//         log.info("Resources: " +
//                  StringUtil.toString(getActiveResources().iterator()));
//         log.info("JVM Args: " + StringUtil.toString(_jvmargs.iterator()));
//         log.info("App Args: " + StringUtil.toString(_appargs.iterator()));

        // this will read in the contents of the digest file and validate itself
        try {
            _digest = new Digest(_appdir);
        } catch (IOException ioe) {
            log.info("Failed to load digest: " + ioe.getMessage() + ". Attempting recovery...");
        }

        // if we have no version, then we are running in unversioned mode so we need to download
        // our digest.txt file on every invocation
        if (_version == -1) {
            // make a note of the old meta-digest, if this changes we need to revalidate all of our
            // resources as one or more of them have also changed
            String olddig = (_digest == null) ? "" : _digest.getMetaDigest();
            try {
                status.updateStatus("m.checking");
                downloadDigestFile();
                _digest = new Digest(_appdir);
                if (!olddig.equals(_digest.getMetaDigest())) {
                    log.info("Unversioned digest changed. Revalidating...");
                    status.updateStatus("m.validating");
                    clearValidationMarkers();
                }
            } catch (IOException ioe) {
                log.warning("Failed to refresh non-versioned digest: " +
                            ioe.getMessage() + ". Proceeding...");
            }
        }

        // regardless of whether we're versioned, if we failed to read the digest from disk, try to
        // redownload the digest file and give it another good college try; this time we allow
        // exceptions to propagate up to the caller as there is nothing else we can do
        if (_digest == null) {
            status.updateStatus("m.updating_metadata");
            downloadDigestFile();
            _digest = new Digest(_appdir);
        }

        // now verify the contents of our main config file
        Resource crsrc = getConfigResource();
        if (!_digest.validateResource(crsrc, null)) {
            status.updateStatus("m.updating_metadata");
            // attempt to redownload both of our metadata files; again we pass errors up to our
            // caller because there's nothing we can do to automatically recover
            downloadConfigFile();
            downloadDigestFile();
            _digest = new Digest(_appdir);
            // revalidate everything if we end up downloading new metadata
            clearValidationMarkers();
            // if the new copy validates, reinitialize ourselves; otherwise report baffling hoseage
            if (_digest.validateResource(crsrc, null)) {
                init(true);
            } else {
                log.warning(CONFIG_FILE + " failed to validate even after redownloading. " +
                            "Blindly forging onward.");
            }
        }

        // start by assuming we are happy with our version
        _targetVersion = _version;

        // if we are a versioned application, read in the contents of the version.txt file
        if (_version != -1) {
            File vfile = getLocalPath(VERSION_FILE);
            FileInputStream fin = null;
            try {
                fin = new FileInputStream(vfile);
                BufferedReader bin = new BufferedReader(new InputStreamReader(fin));
                String vstr = bin.readLine();
                if (!StringUtil.isBlank(vstr)) {
                    _targetVersion = Long.parseLong(vstr);
                }
            } catch (Exception e) {
                log.info("Unable to read version file: " + e.getMessage());
            } finally {
                StreamUtil.close(fin);
            }
        }

        // finally let the caller know if we need an update
        return _version != _targetVersion;
    }

    /**
     * Verifies the code and media resources associated with this application. A list of resources
     * that do not exist or fail the verification process will be returned. If all resources are
     * ready to go, null will be returned and the application is considered ready to run.
     *
     * @param alreadyValid if non-null a 1 element array that will have the number of "already
     * validated" resources filled in.
     */
    public List<Resource> verifyResources (ProgressObserver obs, int[] alreadyValid)
        throws InterruptedException
    {
        List<Resource> rsrcs = getAllResources();
        List<Resource> failures = new ArrayList<Resource>();

        // total up the file size of the resources to validate
        long totalSize = 0L;
        for (Resource rsrc : rsrcs) {
            totalSize += rsrc.getLocal().length();
        }

        MetaProgressObserver mpobs = new MetaProgressObserver(obs, totalSize);
        for (Resource rsrc : rsrcs) {
            if (Thread.interrupted()) {
                throw new InterruptedException("m.applet_stopped");
            }
            mpobs.startElement(rsrc.getLocal().length());

            if (rsrc.isMarkedValid()) {
                if (alreadyValid != null) {
                    alreadyValid[0]++;
                }
                mpobs.progress(100);
                continue;
            }

            try {
                if (_digest.validateResource(rsrc, mpobs)) {
                    // unpack this resource if appropriate
                    if (!rsrc.shouldUnpack() || rsrc.unpack()) {
                        // finally note that this resource is kosher
                        rsrc.markAsValid();
                        continue;
                    }
                    log.info("Failure unpacking resource [rsrc=" + rsrc + "].");
                }

            } catch (Exception e) {
                log.info("Failure validating resource [rsrc=" + rsrc + ", error=" + e + "]. " +
                         "Requesting redownload...");

            } finally {
                mpobs.progress(100);
            }
            failures.add(rsrc);
        }

        return (failures.size() == 0) ? null : failures;
    }

    /**
     * Clears all validation marker files.
     */
    public void clearValidationMarkers ()
    {
        clearValidationMarkers(getAllResources().iterator());
    }

    /**
     * Creates a versioned application base URL for the specified version.
     */
    protected URL createVAppBase (long version)
        throws MalformedURLException
    {
        return new URL(StringUtil.replace(_appbase, "%VERSION%", "" + version));
    }

    /**
     * Clears all validation marker files for the resources in the supplied iterator.
     */
    protected void clearValidationMarkers (Iterator<Resource> iter)
    {
        while (iter.hasNext()) {
            iter.next().clearMarker();
        }
    }

    /**
     * Downloads a new copy of CONFIG_FILE.
     */
    protected void downloadConfigFile ()
        throws IOException
    {
        downloadControlFile(CONFIG_FILE, false);
    }

    /**
     * @return true if gettingdown.lock was unlocked, already locked by this application or if
     * we're not locking at all.
     */
    public synchronized boolean lockForUpdates ()
    {
        if (_lock != null && _lock.isValid()) {
            return true;
        }
        try {
            _lockChannel = new RandomAccessFile(getLocalPath("gettingdown.lock"), "rw").getChannel();
        } catch (FileNotFoundException e) {
            log.warning("Unable to create lock file [message=" + e.getMessage() + "]", e);
            return false;
        }
        try {
            _lock = _lockChannel.tryLock();
        } catch (IOException e) {
            log.warning("Unable to create lock [message=" + e.getMessage() + "]", e);
            return false;
        } catch (OverlappingFileLockException e) {
            log.warning("The lock is held elsewhere in this JVM", e);
            return false;
        }
        log.info("Able to lock for updates: " + (_lock != null));
        return _lock != null;
    }

    /**
     * Release gettingdown.lock
     */
    public synchronized void releaseLock ()
    {
        if (_lock != null) {
            log.info("Releasing lock");
            try {
                _lock.release();
            } catch (IOException e) {
                log.warning("Unable to release lock [message=" + e.getMessage() + "]", e);
            }
            try {
                _lockChannel.close();
            } catch (IOException e) {
                log.warning("Unable to close lock channel [message=" + e.getMessage() + "]", e);
            }
            _lockChannel = null;
            _lock = null;
        }
    }

    /**
     * Downloads a copy of Digest.DIGEST_FILE and validates its signature.
     * @throws IOException
     */
    protected void downloadDigestFile ()
        throws IOException
    {
        downloadControlFile(Digest.DIGEST_FILE, true);
    }

    /**
     * Downloads a new copy of the specified control file, optionally validating its signature.
     * If the download is successful, moves it over the old file on the filesystem.
     *
     * <p> We implement simple signing of the digest.txt file for use with the Getdown applet, but
     * this should never be used as-is with a non-applet getdown installation, as the signing
     * format has no provisions for declaring arbitrary signing key IDs, signature algorithm, et al
     * -- it is entirely reliant on the ability to upgrade the Getdown applet, and its signature
     * validation implementation, at-will (ie, via an Applet).
     *
     * <p> TODO: Switch to PKCS #7 or CMS.
     */
    protected void downloadControlFile (String path, boolean validateSignature)
        throws IOException
    {
        File target = downloadFile(path);

        if (validateSignature) {
            if (_signers == null) {
                log.info("No signers, not verifying file [path=" + path + "].");

            } else {
                File signatureFile = downloadFile(path + SIGNATURE_SUFFIX);
                byte[] signature = null;
                FileReader reader = null;
                try {
                    reader = new FileReader(signatureFile);
                    signature = StreamUtil.toByteArray(new FileInputStream(signatureFile));
                } finally {
                    StreamUtil.close(reader);
                    signatureFile.delete(); // delete the file regardless
                }

                byte[] buffer = new byte[8192];
                int length, validated = 0;
                for (Object signer : _signers) {
                    if (!(signer instanceof Certificate)) {
                        continue;
                    }

                    Certificate cert = (Certificate)signer;
                    FileInputStream dataInput = null;
                    try {
                        dataInput = new FileInputStream(target);
                        Signature sig = Signature.getInstance("SHA1withRSA");
                        sig.initVerify(cert);
                        while ((length = dataInput.read(buffer)) != -1) {
                            sig.update(buffer, 0, length);
                        }

                        if (!sig.verify(Base64.decodeBase64(signature))) {
                            log.info("Signature does not match [cert=" + cert.getPublicKey() + "]");
                            continue;
                        } else {
                            log.info("Signature matches [cert=" + cert.getPublicKey() + "]");
                            validated++;
                        }

                    } catch (IOException ioe) {
                        log.warning("Failure validating signature of " + target + ": " + ioe);

                    } catch (GeneralSecurityException gse) {
                        // no problem!

                    } finally {
                        StreamUtil.close(dataInput);
                        dataInput = null;
                    }
                }

                // if we couldn't find a key that validates our digest, we are the hosed!
                if (validated == 0) {
                    // delete the temporary digest file as we know it is invalid
                    target.delete();
                    throw new IOException("m.corrupt_digest_signature_error");
                }
            }
        }

        // now move the temporary file over the original
        File original = getLocalPath(path);
        if (!FileUtil.renameTo(target, original)) {
            throw new IOException("Failed to rename(" + target + ", " + original + ")");
        }
    }

    /**
     * Download a path to a temporary file, returning a {@link File} instance with the path
     * contents.
     */
    protected File downloadFile (String path)
        throws IOException
    {
        File target = getLocalPath(path + "_new");

        URL targetURL = null;
        try {
            targetURL = getRemoteURL(path);
        } catch (Exception e) {
            log.warning("Requested to download invalid control file [appbase=" + _vappbase +
                        ", path=" + path + ", error=" + e + "].");
            throw (IOException) new IOException("Invalid path '" + path + "'.").initCause(e);
        }

        log.info("Attempting to refetch '" + path + "' from '" + targetURL + "'.");

        // stream the URL into our temporary file
        InputStream fin = null;
        FileOutputStream fout = null;
        try {
            fin = targetURL.openStream();
            fout = new FileOutputStream(target);
            StreamUtil.copy(fin, fout);
        } finally {
            StreamUtil.close(fin);
            StreamUtil.close(fout);
        }

        return target;
    }

    /** Helper function for creating {@link Resource} instances. */
    protected Resource createResource (String path, boolean unpack)
        throws MalformedURLException
    {
        return new Resource(path, getRemoteURL(path), getLocalPath(path), unpack);
    }

    /** Used to parse resources with the specfied name. */
    protected void parseResources (HashMap<String,Object> cdata, String name, boolean unpack,
                                   ArrayList<Resource> list)
    {
        String[] rsrcs = ConfigUtil.getMultiValue(cdata, name);
        if (rsrcs == null) {
            return;
        }
        for (String rsrc : rsrcs) {
            try {
                list.add(createResource(rsrc, unpack));
            } catch (Exception e) {
                log.warning("Invalid resource '" + rsrc + "'. " + e);
            }
        }
    }

    /** Used to parse rectangle specifications from the config file. */
    protected Rectangle parseRect (HashMap<String,Object> cdata, String name, Rectangle def)
    {
        String value = (String)cdata.get(name);
        Rectangle rect = parseRect(value);
        if (rect == null) {
            log.warning("Ignoring invalid '" + name + "' config '" + value + "'.");
        } else {
            return rect;
        }
        return def;
    }

    /**
     * Takes a comma-separated String of four integers and returns a rectangle using those ints as
     * the its x, y, width, and height.
     */
    public static Rectangle parseRect (String value)
    {
        if (!StringUtil.isBlank(value)) {
            int[] v = StringUtil.parseIntArray(value);
            if (v != null && v.length == 4) {
                return new Rectangle(v[0], v[1], v[2], v[3]);
            }
        }
        return null;
    }

    /** Used to parse color specifications from the config file. */
    protected Color parseColor (HashMap<String,Object> cdata, String name, Color def)
    {
        return parseColor(cdata, name, def, true);
    }

    /** Used to parse color specifications from the config file. */
    protected Color parseColor (HashMap<String, Object> cdata, String name, Color def,
        boolean required)
    {
        String value = (String)cdata.get(name);
        Color color = parseColor(value);
        if (color == null && required) {
            log.warning("Ignoring invalid '" + name + "' config '" + value + "'.");
        } else {
            return color;
        }
        return def;
    }

    /**
     * Parses the given hex color value (e.g. FFFFF) and returns a Color object with that value. If
     * the given value is null of not a valid hexadecimal number, this will return null.
     */
    public static Color parseColor (String hexValue)
    {
        if (!StringUtil.isBlank(hexValue)) {
            try {
                return new Color(Integer.parseInt(hexValue, 16));
            } catch (NumberFormatException e) {
                log.warning("Ignoring invalid color", "hexValue", hexValue, "exception", e);
            }
        }
        return null;
    }

    /** Parses a list of strings from the config file. */
    protected String[] parseList (HashMap<String,Object> cdata, String name)
    {
        String value = (String)cdata.get(name);
        return (value == null) ? new String[0] : StringUtil.parseStringArray(value);
    }

    /** Possibly generates and returns a google analytics tracking cookie. */
    protected String getGATrackingCode ()
    {
        if (_trackingGAHash == null) {
            return "";
        }
        long time = System.currentTimeMillis() / 1000;
        if (_trackingStart == 0) {
            _trackingStart = time;
        }
        if (_trackingId == 0) {
            _trackingId = RandomUtil.getInRange(100000000, 1000000000);
        }
        StringBuilder cookie = new StringBuilder("&utmcc=__utma%3D").append(_trackingGAHash);
        cookie.append(".").append(_trackingId);
        cookie.append(".").append(_trackingStart).append(".").append(_trackingStart);
        cookie.append(".").append(time).append(".1%3B%2B");
        cookie.append("__utmz%3D").append(_trackingGAHash).append(".");
        cookie.append(_trackingStart).append(".1.1.");
        cookie.append("utmcsr%3D(direct)%7Cutmccn%3D(direct)%7Cutmcmd%3D(none)%3B");
        cookie.append("&utmn=").append(RandomUtil.getInRange(1000000000, 2000000000));
        return cookie.toString();
    }

    protected File _appdir;
    protected String _appid;
    protected File _config;
    protected Digest _digest;

    protected long _version = -1;
    protected long _targetVersion = -1;
    protected String _appbase;
    protected URL _vappbase;
    protected String _class;
    protected String _name;
    protected String _dockIconPath;
    protected boolean _windebug;
    protected boolean _useTorrent;
    protected boolean _allowOffline;

    protected String _trackingURL;
    protected ArrayIntSet _trackingPcts;
    protected String _trackingCookieName;
    protected String _trackingCookieProperty;
    protected String _trackingURLSuffix;
    protected String _trackingGAHash;
    protected long _trackingStart;
    protected int _trackingId;

    protected int _javaVersion;
    protected String _javaLocation;

    protected ArrayList<Resource> _codes = new ArrayList<Resource>();
    protected ArrayList<Resource> _resources = new ArrayList<Resource>();

    protected ArrayList<String> _auxgroups = new ArrayList<String>();
    protected HashMap<String,ArrayList<Resource>> _auxrsrcs =
        new HashMap<String,ArrayList<Resource>>();
    protected HashMap<String,Boolean> _auxactive = new HashMap<String,Boolean>();

    protected ArrayList<String> _jvmargs = new ArrayList<String>();
    protected ArrayList<String> _appargs = new ArrayList<String>();

    protected String[] _baseJvmArgs = new String[0];
    protected String[] _baseAppArgs = new String[0];

    protected Object[] _signers;

    /** If a warning has been issued about not being able to set modtimes. */
    protected boolean _warnedAboutSetLastModified;

    protected static final String[] SA_PROTO = new String[0];

    /** Locks gettingdown.lock in the app dir. Held the entire time updating is going on.*/
    protected FileLock _lock;

    /** Channel to the file underying _lock.  Kept around solely so the lock doesn't close. */
    protected FileChannel _lockChannel;
}
