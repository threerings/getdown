//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2014 Three Rings Design, Inc.
// https://raw.github.com/threerings/getdown/master/LICENSE

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
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
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
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JApplet;

import org.apache.commons.codec.binary.Base64;

import com.samskivert.io.StreamUtil;
import com.samskivert.text.MessageUtil;
import com.samskivert.util.ArrayUtil;
import com.samskivert.util.RandomUtil;
import com.samskivert.util.RunAnywhere;
import com.samskivert.util.StringUtil;

import com.threerings.getdown.launcher.RotatingBackgrounds;
import com.threerings.getdown.util.ConfigUtil;
import com.threerings.getdown.util.ConnectionUtil;
import com.threerings.getdown.util.FileUtil;
import com.threerings.getdown.util.LaunchUtil;
import com.threerings.getdown.util.MetaProgressObserver;
import com.threerings.getdown.util.ProgressObserver;
import com.threerings.getdown.util.VersionUtil;

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
        /**
         * The major steps involved in updating, along with some arbitrary percentages
         * assigned to them, to mark global progress.
         */
        public enum Step
        {
            UPDATE_JAVA(10),
            VERIFY_METADATA(15, 65, 95),
            DOWNLOAD(40),
            PATCH(60),
            VERIFY_RESOURCES(70, 97),
            REDOWNLOAD_RESOURCES(90),
            UNPACK(98),
            LAUNCH(99);

            /** What is the final percent value for this step? */
            public final List<Integer> defaultPercents;

            /** Enum constructor. */
            Step (int... percents)
            {
                this.defaultPercents = intsToList(percents);
            }
        }

        /** The human readable name of this application. */
        public String name;

        /** A background color, just in case. */
        public Color background = Color.white;

        /** Background image specifiers for {@link RotatingBackgrounds}. */
        public String[] rotatingBackgrounds;

        /** The error background image for {@link RotatingBackgrounds}. */
        public String errorBackground;

        /** The paths (relative to the appdir) of images for the window icon. */
        public String[] iconImages;

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

        /** The dimensions of the patch notes button. */
        public Rectangle patchNotes = new Rectangle(5, 50, 112, 26);

        /** The patch notes URL. */
        public String patchNotesUrl;

        /** The dimensions of the play again button. */
        public Rectangle playAgain;

        /** The path (relative to the appdir) to a single play again image. */
        public String playAgainImage;

        /** The global percentages for each step. A step may have more than one, and
         * the lowest reasonable one is used if a step is revisited. */
        public Map<Step, List<Integer>> stepPercentages =
            new EnumMap<Step, List<Integer>>(Step.class);

        /** Generates a string representation of this instance. */
        @Override
        public String toString ()
        {
            return "[name=" + name + ", bg=" + background + ", bg=" + backgroundImage +
                ", pi=" + progressImage + ", prect=" + progress + ", pt=" + progressText +
                ", pb=" + progressBar + ", srect=" + status + ", st=" + statusText +
                ", shadow=" + textShadow + ", err=" + installError + ", nrect=" + patchNotes +
                ", notes=" + patchNotesUrl + ", stepPercentages=" + stepPercentages +
                ", parect=" + playAgain + ", paimage=" + playAgainImage + "]";
        }

        /** Initializer */
        {
            for (Step step : Step.values()) {
                stepPercentages.put(step, step.defaultPercents);
            }
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
     * Contains metadata for an auxiliary resource group.
     */
    public static class AuxGroup {
        public final String name;
        public final List<Resource> codes;
        public final List<Resource> rsrcs;

        public AuxGroup (String name, List<Resource> codes, List<Resource> rsrcs) {
            this.name = name;
            this.codes = Collections.unmodifiableList(codes);
            this.rsrcs = Collections.unmodifiableList(rsrcs);
        }
    }

    /**
     * Creates an application instance with no signers.
     *
     * @see #Application(File, String, Object[], String[], String[])
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
     * be launched. That application will use {@code appid.class} and {@code appid.apparg} to
     * configure itself but all other parameters will be the same as the primary application.
     * @param signers a list of possible signers of this application. Used to verify the digest.
     * @param jvmargs additional arguments to pass on to launched jvms.
     * @param appargs additional arguments to pass on to launched application; these will be added
     * after the args in the getdown.txt file.
     */
    public Application (File appdir, String appid, List<Certificate> signers,
                        String[] jvmargs, String[] appargs)
    {
        _appdir = appdir;
        _appid = appid;
        _signers = (signers == null) ? Collections.<Certificate>emptyList() : signers;
        _config = getLocalPath(CONFIG_FILE);
        _extraJvmArgs = (jvmargs == null) ? ArrayUtil.EMPTY_STRING : jvmargs;
        _extraAppArgs = (appargs == null) ? ArrayUtil.EMPTY_STRING : appargs;
    }

    /**
     * Returns a resource that refers to the application configuration file itself.
     */
    public Resource getConfigResource ()
    {
        try {
            return createResource(CONFIG_FILE, false);
        } catch (Exception e) {
            throw new RuntimeException("Invalid appbase '" + _vappbase + "'.", e);
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
     * Returns a list of all the active {@link Resource} objects used by this application (code and
     * non-code).
     */
    public List<Resource> getAllActiveResources ()
    {
        List<Resource> allResources = new ArrayList<Resource>();
        allResources.addAll(getActiveCodeResources());
        allResources.addAll(getActiveResources());
        return allResources;
    }

    /**
     * Returns the auxiliary resource group with the specified name, or null.
     */
    public AuxGroup getAuxGroup (String name)
    {
        return _auxgroups.get(name);
    }

    /**
     * Returns the set of all auxiliary resource groups defined by the application. An auxiliary
     * resource group is a collection of resource files that are not downloaded unless a group
     * token file is present in the application directory.
     */
    public Iterable<AuxGroup> getAuxGroups ()
    {
        return _auxgroups.values();
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
     * Returns all main code resources and all code resources from active auxiliary resource groups.
     */
    public List<Resource> getActiveCodeResources ()
    {
        ArrayList<Resource> codes = new ArrayList<Resource>();
        codes.addAll(getCodeResources());
        for (AuxGroup aux : getAuxGroups()) {
            if (isAuxGroupActive(aux.name)) {
                codes.addAll(aux.codes);
            }
        }
        return codes;
    }

    /**
     * Returns all non-code resources and all resources from active auxiliary resource groups.
     */
    public List<Resource> getActiveResources ()
    {
        ArrayList<Resource> rsrcs = new ArrayList<Resource>();
        rsrcs.addAll(getResources());
        for (AuxGroup aux : getAuxGroups()) {
            if (isAuxGroupActive(aux.name)) {
                rsrcs.addAll(aux.rsrcs);
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
            log.warning("Requested patch resource for up-to-date or non-versioned application",
                "cvers", _version, "tvers", _targetVersion);
            return null;
        }

        String infix = (auxgroup == null) ? "" : ("-" + auxgroup);
        String pfile = "patch" + infix + _version + ".dat";
        try {
            URL remote = new URL(createVAppBase(_targetVersion), pfile);
            return new Resource(pfile, remote, getLocalPath(pfile), false);
        } catch (Exception e) {
            log.warning("Failed to create patch resource path",
                "pfile", pfile, "appbase", _appbase, "tvers", _targetVersion, "error", e);
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
            log.warning("Failed to create VM resource", "vmfile", vmfile, "appbase", _appbase,
                "tvers", _targetVersion, "javaloc", _javaLocation, "error", e);
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
            log.warning("Failed to create full resource path",
                "file", file, "appbase", _appbase, "tvers", _targetVersion, "error", e);
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
            return _trackingURL == null ? null : new URL(_trackingURL + event + suffix + ga);
        } catch (MalformedURLException mue) {
            log.warning("Invalid tracking URL", "path", _trackingURL, "event", event, "error", mue);
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
     * Instructs the application to parse its {@code getdown.txt} configuration and prepare itself
     * for operation. The application base URL will be parsed first so that if there are errors
     * discovered later, the caller can use the application base to download a new {@code
     * getdown.txt} file and try again.
     *
     * @return a configured UpdateInterface instance that will be used to configure the update UI.
     *
     * @exception IOException thrown if there is an error reading the file or an error encountered
     * during its parsing.
     */
    public UpdateInterface init (boolean checkPlatform)
        throws IOException
    {
        Map<String,Object> cdata = null;
        File config = _config;
        try {
            // if we have a configuration file, read the data from it
            if (config.exists()) {
                cdata = ConfigUtil.parseConfig(_config, checkPlatform);
            }
            // otherwise, try reading data from our backup config file; thanks to funny windows
            // bullshit, we have to do this backup file fiddling in case we got screwed while
            // updating getdown.txt during normal operation
            else if ((config = getLocalPath(CONFIG_FILE + "_old")).exists()) {
                cdata = ConfigUtil.parseConfig(config, checkPlatform);
            }
            // otherwise, issue a warning that we found no getdown file
            else {
                log.info("Found no getdown.txt file", "appdir", _appdir);
            }
        } catch (Exception e) {
            log.warning("Failure reading config file", "file", config, e);
        }

        // if we failed to read our config file, check for an appbase specified via a system
        // property; we can use that to bootstrap ourselves back into operation
        if (cdata == null) {
            String appbase = SysProps.appBase();
            log.info("Attempting to obtain 'appbase' from system property", "appbase", appbase);
            cdata = new HashMap<String,Object>();
            cdata.put("appbase", appbase);
        }

        // first determine our application base, this way if anything goes wrong later in the
        // process, our caller can use the appbase to download a new configuration file
        _appbase = (String)cdata.get("appbase");
        if (_appbase == null) {
            throw new RuntimeException("m.missing_appbase");
        }
        // make sure there's a trailing slash
        if (!_appbase.endsWith("/")) {
            _appbase = _appbase + "/";
        }

        // check if we're overriding the domain in the appbase
        _appbase = replaceDomain(_appbase);

        // extract our version information
        String vstr = (String)cdata.get("version");
        if (vstr != null) _version = parseLong(vstr, "m.invalid_version");

        // check to see if we require a particular max JVM version and have a supplied JVM
        vstr = (String)cdata.get("java_max_version");
        if (vstr != null) _javaMaxVersion = (int)parseLong(vstr, "m.invalid_java_version");

        // if we are a versioned deployment, create a versioned appbase
        try {
            _vappbase = (_version < 0) ? new URL(_appbase) : createVAppBase(_version);
        } catch (MalformedURLException mue) {
            String err = MessageUtil.tcompose("m.invalid_appbase", _appbase);
            throw (IOException) new IOException(err).initCause(mue);
        }

        // check for a latest config URL
        String latest = (String)cdata.get("latest");
        if (latest != null) {
            latest = replaceDomain(latest);
            try {
                _latest = new URL(latest);
            } catch (MalformedURLException mue) {
                log.warning("Invalid URL for latest attribute.", mue);
            }
        }

        String prefix = StringUtil.isBlank(_appid) ? "" : (_appid + ".");

        // determine our application class name
        _class = (String)cdata.get(prefix + "class");
        if (_class == null) {
            throw new IOException("m.missing_class");
        }

        // check to see if we require a particular JVM version and have a supplied JVM
        vstr = (String)cdata.get("java_version");
        if (vstr != null) _javaMinVersion = (int)parseLong(vstr, "m.invalid_java_version");
        // we support java_min_version as an alias of java_version; it better expresses the check
        // that's going on and better mirrors java_max_version
        vstr = (String)cdata.get("java_min_version");
        if (vstr != null) _javaMinVersion = (int)parseLong(vstr, "m.invalid_java_version");

        // check to see if we require a particular JVM version and have a supplied JVM
        vstr = (String)cdata.get("java_exact_version_required");
        _javaExactVersionRequired = Boolean.parseBoolean(vstr);

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
            _trackingPcts = new HashSet<Integer>();
            for (int pct : StringUtil.parseIntArray(trackPcts)) {
                _trackingPcts.add(pct);
            }
        } else if (!StringUtil.isBlank(_trackingURL)) {
            _trackingPcts = new HashSet<Integer>();
            _trackingPcts.add(50);
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
        _jvmargs.clear();
        _appargs.clear();
        _txtJvmArgs.clear();

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
            ArrayList<Resource> codes = new ArrayList<Resource>();
            parseResources(cdata, auxgroup + ".code", false, codes);
            ArrayList<Resource> rsrcs = new ArrayList<Resource>();
            parseResources(cdata, auxgroup + ".resource", false, rsrcs);
            parseResources(cdata, auxgroup + ".uresource", true, rsrcs);
            _auxgroups.put(auxgroup, new AuxGroup(auxgroup, codes, rsrcs));
        }

        // transfer our JVM arguments
        String[] jvmargs = ConfigUtil.getMultiValue(cdata, "jvmarg");
        if (jvmargs != null) {
            for (String jvmarg : jvmargs) {
                _jvmargs.add(jvmarg);
            }
        }

        // Add the launch specific JVM arguments
        for (String arg : _extraJvmArgs) {
            _jvmargs.add(arg);
        }

        // get the set of optimum JVM arguments
        _optimumJvmArgs = ConfigUtil.getMultiValue(cdata, "optimum_jvmarg");

        // transfer our application arguments
        String[] appargs = ConfigUtil.getMultiValue(cdata, prefix + "apparg");
        if (appargs != null) {
            for (String apparg : appargs) {
                _appargs.add(apparg);
            }
        }

        // add the launch specific application arguments
        for (String arg : _extraAppArgs) {
            _appargs.add(arg);
        }

        // look for custom arguments
        fillAssignmentListFromPairs("extra.txt", _txtJvmArgs);

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
        ui.textShadow = parseColor(cdata, "ui.text_shadow", ui.textShadow);
        ui.backgroundImage = (String)cdata.get("ui.background_image");
        if (ui.backgroundImage == null) { // support legacy format
            ui.backgroundImage = (String)cdata.get("ui.background");
        }
        // and now ui.background can refer to the background color, but fall back to black
        // or white, depending on the brightness of the progressText
        Color defaultBackground = (.5f < Color.RGBtoHSB(
                ui.progressText.getRed(), ui.progressText.getGreen(), ui.progressText.getBlue(),
                null)[2])
            ? Color.BLACK
            : Color.WHITE;
        ui.background = parseColor(cdata, "ui.background", defaultBackground);
        ui.progressImage = (String)cdata.get("ui.progress_image");
        ui.rotatingBackgrounds = ConfigUtil.getMultiValue(cdata, "ui.rotating_background");
        ui.iconImages = ConfigUtil.getMultiValue(cdata, "ui.icon");
        ui.errorBackground = (String)cdata.get("ui.error_background");
        _dockIconPath = (String)cdata.get("ui.mac_dock_icon");
        if (_dockIconPath == null) {
            _dockIconPath = "../desktop.icns"; // use a sensible default
        }

        // On an installation error, where do we point the user.
        String installError = parseUrl(cdata, "ui.install_error", null);
        ui.installError = (installError == null) ?
            "m.default_install_error" : MessageUtil.taint(installError);

        // the patch notes bits
        ui.patchNotes = parseRect(cdata, "ui.patch_notes", ui.patchNotes);
        ui.patchNotesUrl = parseUrl(cdata, "ui.patch_notes_url", null);

        // the play again bits
        ui.playAgain = parseRect(cdata, "ui.play_again", ui.playAgain);
        ui.playAgainImage = (String)cdata.get("ui.play_again_image");

        // step progress percentages
        for (UpdateInterface.Step step : UpdateInterface.Step.values()) {
            String spec = (String)cdata.get("ui.percents." + step.name());
            if (spec != null) {
                try {
                    ui.stepPercentages.put(step, intsToList(StringUtil.parseIntArray(spec)));
                } catch (Exception e) {
                    log.warning("Failed to parse percentages for " + step + ": " + spec);
                }
            }
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
                    if (pair[1].length() == 0) {
                        collector.add(pair[0]);
                    } else {
                        collector.add(pair[0] + "=" + pair[1]);
                    }
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
        if (_javaMinVersion == 0 && _javaMaxVersion == 0) {
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
            log.warning("Unable to parse VM version, hoping for the best",
                        "version", verstr, "needed", _javaMinVersion);
            return true;
        }

        int major = Integer.parseInt(m.group(1));
        int minor = Integer.parseInt(m.group(2));
        int revis = Integer.parseInt(m.group(3));
        int patch = m.group(4) == null ? 0 : Integer.parseInt(m.group(4).substring(1));
        int version = patch + 100 * (revis + 100 * (minor + 100 * major));

        if (_javaExactVersionRequired) {
            if (version == _javaMinVersion) {
                return true;
            } else {
                log.warning("An exact Java VM version is required.", "current", version,
                            "required", _javaMinVersion);
                return false;
            }
        }

        boolean minVersionOK = (_javaMinVersion == 0) || (version >= _javaMinVersion);
        boolean maxVersionOK = (_javaMaxVersion == 0) || (version <= _javaMaxVersion);
        return minVersionOK && maxVersionOK;
    }

    /**
     * Checks whether the app has a set of "optimum" JVM args that we wish to try first, detecting
     * whether the launch is successful and, if necessary, trying again without the optimum
     * arguments.
     */
    public boolean hasOptimumJvmArgs ()
    {
        return _optimumJvmArgs != null;
    }

    /**
     * Returns true if the app should attempt to run even if we have no Internet connection.
     */
    public boolean allowOffline ()
    {
        return _allowOffline;
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
     *
     * @param optimum whether or not to include the set of optimum arguments (as opposed to falling
     * back).
     */
    public Process createProcess (boolean optimum)
        throws IOException
    {
        // create our classpath
        StringBuilder cpbuf = new StringBuilder();
        for (Resource rsrc : getActiveCodeResources()) {
            if (cpbuf.length() > 0) {
                cpbuf.append(File.pathSeparator);
            }
            cpbuf.append(rsrc.getLocal().getAbsolutePath());
        }

        ArrayList<String> args = new ArrayList<String>();

        // reconstruct the path to the JVM
        args.add(LaunchUtil.getJVMPath(_appdir, _windebug || optimum));

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

        // add the marker indicating the app is running in getdown
        args.add("-D" + Properties.GETDOWN + "=true");

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

        // add the optimum arguments if requested and available
        if (optimum && _optimumJvmArgs != null) {
            for (String string : _optimumJvmArgs) {
                args.add(processArg(string));
            }
        }

        // add the arguments from extra.txt (after the optimum ones, in case they override them)
        for (String string : _txtJvmArgs) {
            args.add(processArg(string));
        }

        // add the application class name
        args.add(_class);

        // finally add the application arguments
        for (String string : _appargs) {
            args.add(processArg(string));
        }

        String[] envp = createEnvironment();
        String[] sargs = args.toArray(new String[args.size()]);
        log.info("Running " + StringUtil.join(sargs, "\n  "));

        return Runtime.getRuntime().exec(sargs, envp, _appdir);
    }

    /**
     * If the application provided environment variables, combine those with the current
     * environment and return that in a style usable for {@link Runtime#exec(String, String[])}.
     * If the application didn't provide any environment variables, null is returned to just use
     * the existing environment.
     */
    protected String[] createEnvironment ()
    {
        List<String> envvar = new ArrayList<String>();
        fillAssignmentListFromPairs("env.txt", envvar);
        if (envvar.isEmpty()) {
            log.info("Didn't find any custom environment variables, not setting any.");
            return null;
        }

        List<String> envAssignments = new ArrayList<String>();
        for (String assignment : envvar) {
            envAssignments.add(processArg(assignment));
        }
        for (Entry<String, String> environmentEntry : System.getenv().entrySet()) {
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
        for (Resource rsrc : getActiveCodeResources()) {
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
        arg = arg.replace("%APPDIR%", _appdir.getAbsolutePath());
        arg = arg.replace("%VERSION%", String.valueOf(_version));
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
        // and/or check the latest config URL for a newer version
        if (_version != -1) {
            File vfile = getLocalPath(VERSION_FILE);
            long fileVersion = VersionUtil.readVersion(vfile);
            if (fileVersion != -1) {
                _targetVersion = fileVersion;
            }

            if (_latest != null) {
                InputStream in = null;
                PrintStream out = null;
                try {
                    in = ConnectionUtil.open(_latest).getInputStream();
                    BufferedReader bin = new BufferedReader(new InputStreamReader(in));
                    for (String[] pair : ConfigUtil.parsePairs(bin, false)) {
                        if (pair[0].equals("version")) {
                            _targetVersion = Math.max(Long.parseLong(pair[1]), _targetVersion);
                            if (fileVersion != -1 && _targetVersion > fileVersion) {
                                // replace the file with the newest version
                                out = new PrintStream(new FileOutputStream(vfile));
                                out.println(_targetVersion);
                            }
                            break;
                        }
                    }
                } catch (Exception e) {
                    log.warning("Unable to retrieve version from latest config file.", e);
                } finally {
                    StreamUtil.close(in);
                    StreamUtil.close(out);
                }
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
     * @param unpacked a set to populate with unpacked resources.
     */
    public List<Resource> verifyResources (
        ProgressObserver obs, int[] alreadyValid, Set<Resource> unpacked)
            throws InterruptedException
    {
        List<Resource> rsrcs = getAllActiveResources();
        List<Resource> failures = new ArrayList<Resource>();

        // total up the file size of the resources to validate
        long totalSize = 0L;
        for (Resource rsrc : rsrcs) {
            totalSize += rsrc.getLocal().length();
        }

        MetaProgressObserver mpobs = new MetaProgressObserver(obs, totalSize);
        boolean noUnpack = SysProps.noUnpack();
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
                    if (noUnpack || !rsrc.shouldUnpack()) {
                        // finally note that this resource is kosher
                        rsrc.markAsValid();
                        continue;
                    }
                    if (rsrc.unpack()) {
                        unpacked.add(rsrc);
                        rsrc.markAsValid();
                        continue;
                    }
                    log.info("Failure unpacking resource", "rsrc", rsrc);
                }

            } catch (Exception e) {
                log.info("Failure validating resource. Requesting redownload...",
                    "rsrc", rsrc, "error", e);

            } finally {
                mpobs.progress(100);
            }
            failures.add(rsrc);
        }

        return (failures.size() == 0) ? null : failures;
    }

    /**
     * Unpacks the resources that require it (we know that they're valid).
     *
     * @param unpacked a set of resources to skip because they're already unpacked.
     */
    public void unpackResources (ProgressObserver obs, Set<Resource> unpacked)
        throws InterruptedException
    {
        List<Resource> rsrcs = getActiveResources();

        // total up the file size of the resources to unpack
        long totalSize = 0L;
        for (Iterator<Resource> it = rsrcs.iterator(); it.hasNext(); ) {
            Resource rsrc = it.next();
            if (rsrc.shouldUnpack() && !unpacked.contains(rsrc)) {
                totalSize += rsrc.getLocal().length();
            } else {
                it.remove();
            }
        }

        MetaProgressObserver mpobs = new MetaProgressObserver(obs, totalSize);
        for (Resource rsrc : rsrcs) {
            if (Thread.interrupted()) {
                throw new InterruptedException("m.applet_stopped");
            }
            mpobs.startElement(rsrc.getLocal().length());
            if (!rsrc.unpack()) {
                log.info("Failure unpacking resource", "rsrc", rsrc);
            }
            mpobs.progress(100);
        }
    }

    /**
     * Clears all validation marker files.
     */
    public void clearValidationMarkers ()
    {
        clearValidationMarkers(getAllActiveResources().iterator());
    }

    /**
     * Returns the version number for the application.  Should only be called after successful
     * return of verifyMetadata.
     */
    public long getVersion ()
    {
        return _version;
    }

    /**
     * Creates a versioned application base URL for the specified version.
     */
    protected URL createVAppBase (long version)
        throws MalformedURLException
    {
        return new URL(_appbase.replace("%VERSION%", "" + version));
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
            log.warning("Unable to create lock file", "message", e.getMessage(), e);
            return false;
        }
        try {
            _lock = _lockChannel.tryLock();
        } catch (IOException e) {
            log.warning("Unable to create lock", "message", e.getMessage(), e);
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
                log.warning("Unable to release lock", "message", e.getMessage(), e);
            }
            try {
                _lockChannel.close();
            } catch (IOException e) {
                log.warning("Unable to close lock channel", "message", e.getMessage(), e);
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
            if (_signers.isEmpty()) {
                log.info("No signers, not verifying file", "path", path);

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
                for (Certificate cert : _signers) {
                    FileInputStream dataInput = null;
                    try {
                        dataInput = new FileInputStream(target);
                        Signature sig = Signature.getInstance("SHA1withRSA");
                        sig.initVerify(cert);
                        while ((length = dataInput.read(buffer)) != -1) {
                            sig.update(buffer, 0, length);
                        }

                        if (!sig.verify(Base64.decodeBase64(signature))) {
                            log.info("Signature does not match", "cert", cert.getPublicKey());
                            continue;
                        } else {
                            log.info("Signature matches", "cert", cert.getPublicKey());
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
            log.warning("Requested to download invalid control file",
                "appbase", _vappbase, "path", path, "error", e);
            throw (IOException) new IOException("Invalid path '" + path + "'.").initCause(e);
        }

        log.info("Attempting to refetch '" + path + "' from '" + targetURL + "'.");

        // stream the URL into our temporary file
        InputStream fin = null;
        FileOutputStream fout = null;
        try {
            URLConnection uconn = ConnectionUtil.open(targetURL);
            // we have to tell Java not to use caches here, otherwise it will cache any request for
            // same URL for the lifetime of this JVM (based on the URL string, not the URL object);
            // if the getdown.txt file, for example, changes in the meanwhile, we would never hear
            // about it; turning off caches is not a performance concern, because when Getdown asks
            // to download a file, it expects it to come over the wire, not from a cache
            uconn.setUseCaches(false);
            // configure a connect timeout if requested
            int ctimeout = SysProps.connectTimeout();
            if (ctimeout > 0) {
                uconn.setConnectTimeout(ctimeout * 1000);
            }
            fin = uconn.getInputStream();
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

    /** Used to parse resources with the specified name. */
    protected void parseResources (Map<String,Object> cdata, String name, boolean unpack,
                                   List<Resource> list)
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
    protected Rectangle parseRect (Map<String,Object> cdata, String name, Rectangle def)
    {
        String value = (String)cdata.get(name);
        Rectangle rect = parseRect(name, value);
        return (rect == null) ? def : rect;
    }

    /**
     * Make an immutable List from the specified int array.
     */
    public static List<Integer> intsToList (int[] values)
    {
        List<Integer> list = new ArrayList<Integer>(values.length);
        for (int val : values) {
            list.add(val);
        }
        return Collections.unmodifiableList(list);
    }

    /**
     * Takes a comma-separated String of four integers and returns a rectangle using those ints as
     * the its x, y, width, and height.
     */
    public static Rectangle parseRect (String name, String value)
    {
        if (!StringUtil.isBlank(value)) {
            int[] v = StringUtil.parseIntArray(value);
            if (v != null && v.length == 4) {
                return new Rectangle(v[0], v[1], v[2], v[3]);
            }
            log.warning("Ignoring invalid '" + name + "' config '" + value + "'.");
        }
        return null;
    }

    /** Used to parse color specifications from the config file. */
    protected Color parseColor (Map<String, Object> cdata, String name, Color def)
    {
        String value = (String)cdata.get(name);
        Color color = parseColor(value);
        return (color == null) ? def : color;
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
    protected String[] parseList (Map<String, Object> cdata, String name)
    {
        String value = (String)cdata.get(name);
        return (value == null) ? ArrayUtil.EMPTY_STRING : StringUtil.parseStringArray(value);
    }

    /**
     * Parses a URL from the config file, checking first for a localized version.
     */
    protected String parseUrl (Map<String, Object> cdata, String name, String def)
    {
        String value = (String)cdata.get(name + "." + Locale.getDefault().getLanguage());
        if (!StringUtil.isBlank(value)) {
            return value;
        }
        value = (String)cdata.get(name);
        return StringUtil.isBlank(value) ? def : value;
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

    /**
     * If appbase_domain property is set, replace the domain on the provided string.
     */
    protected String replaceDomain (String appbase)
    {
        String appbaseDomain = SysProps.appbaseDomain();
        if (appbaseDomain != null) {
            Matcher m = Pattern.compile("(http://[^/]+)(.*)").matcher(appbase);
            appbase = m.replaceAll(appbaseDomain + "$2");
        }
        return appbase;
    }

    /**
     * Parses and returns a long. {@code value} must be non-null.
     * @throws IOException on malformed value.
     */
    protected static long parseLong (String value, String errkey) throws IOException
    {
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            String err = MessageUtil.tcompose(errkey, value);
            throw (IOException) new IOException(err).initCause(e);
        }
    }

    protected File _appdir;
    protected String _appid;
    protected File _config;
    protected Digest _digest;

    protected long _version = -1;
    protected long _targetVersion = -1;
    protected String _appbase;
    protected URL _vappbase;
    protected URL _latest;
    protected String _class;
    protected String _name;
    protected String _dockIconPath;
    protected boolean _windebug;
    protected boolean _allowOffline;

    protected String _trackingURL;
    protected Set<Integer> _trackingPcts;
    protected String _trackingCookieName;
    protected String _trackingCookieProperty;
    protected String _trackingURLSuffix;
    protected String _trackingGAHash;
    protected long _trackingStart;
    protected int _trackingId;

    protected int _javaMinVersion, _javaMaxVersion;
    protected boolean _javaExactVersionRequired;
    protected String _javaLocation;

    protected List<Resource> _codes = new ArrayList<Resource>();
    protected List<Resource> _resources = new ArrayList<Resource>();

    protected Map<String,AuxGroup> _auxgroups = new HashMap<String,AuxGroup>();
    protected Map<String,Boolean> _auxactive = new HashMap<String,Boolean>();

    protected List<String> _jvmargs = new ArrayList<String>();
    protected List<String> _appargs = new ArrayList<String>();

    protected String[] _extraJvmArgs;
    protected String[] _extraAppArgs;

    protected String[] _optimumJvmArgs;

    protected List<String> _txtJvmArgs = new ArrayList<String>();

    protected List<Certificate> _signers;

    /** If a warning has been issued about not being able to set modtimes. */
    protected boolean _warnedAboutSetLastModified;

    /** Locks gettingdown.lock in the app dir. Held the entire time updating is going on.*/
    protected FileLock _lock;

    /** Channel to the file underlying _lock.  Kept around solely so the lock doesn't close. */
    protected FileChannel _lockChannel;

    protected static final String[] SA_PROTO = ArrayUtil.EMPTY_STRING;
}
