//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2018 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.data;

import java.io.*;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.security.*;
import java.security.cert.Certificate;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.threerings.getdown.net.Connector;
import com.threerings.getdown.util.*;
// avoid ambiguity with java.util.Base64 which we can't use as it's 1.8+
import com.threerings.getdown.util.Base64;

import static com.threerings.getdown.Log.log;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Parses and provide access to the information contained in the {@code getdown.txt}
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

    /** A special classname that means 'use -jar code.jar' instead of a classname. */
    public static final String MANIFEST_CLASS = "manifest";

    /** Used to communicate information about the UI displayed when updating the application. */
    public static final class UpdateInterface
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
        public final String name;

        /** A background color, just in case. */
        public final int background;

        /** Background image specifiers for `RotatingBackgrounds`. */
        public final List<String> rotatingBackgrounds;

        /** The error background image for `RotatingBackgrounds`. */
        public final String errorBackground;

        /** The paths (relative to the appdir) of images for the window icon. */
        public final List<String> iconImages;

        /** The path (relative to the appdir) to a single background image. */
        public final String backgroundImage;

        /** The path (relative to the appdir) to the progress bar image. */
        public final String progressImage;

        /** The dimensions of the progress bar. */
        public final Rectangle progress;

        /** The color of the progress text. */
        public final int progressText;

        /** The color of the progress bar. */
        public final int progressBar;

        /** The dimensions of the status display. */
        public final Rectangle status;

        /** The color of the status text. */
        public final int statusText;

        /** The color of the text shadow. */
        public final int textShadow;

        /** Where to point the user for help with install errors. */
        public final String installError;

        /** The dimensions of the patch notes button. */
        public final Rectangle patchNotes;

        /** The patch notes URL. */
        public final String patchNotesUrl;

        /** Whether window decorations are hidden for the UI. */
        public final boolean hideDecorations;

        /** Whether progress text should be hidden or not. */
        public final boolean hideProgressText;

        /** The minimum number of seconds to display the GUI. This is to prevent the GUI from
          * flashing up on the screen and immediately disappearing, which can be confusing to the
          * user. */
        public final int minShowSeconds;

        /** The global percentages for each step. A step may have more than one, and
         * the lowest reasonable one is used if a step is revisited. */
        public final Map<Step, List<Integer>> stepPercentages;

        /** Generates a string representation of this instance. */
        @Override
        public String toString ()
        {
            return "[name=" + name + ", bg=" + background + ", bg=" + backgroundImage +
                ", pi=" + progressImage + ", prect=" + progress + ", pt=" + progressText +
                ", pb=" + progressBar + ", srect=" + status + ", st=" + statusText +
                ", shadow=" + textShadow + ", err=" + installError + ", nrect=" + patchNotes +
                ", notes=" + patchNotesUrl + ", stepPercentages=" + stepPercentages +
                ", hideProgressText" + hideProgressText + ", minShow=" + minShowSeconds + "]";
        }

        public UpdateInterface (Config config)
        {
            this.name = config.getString("ui.name");
            this.progress = config.getRect("ui.progress", new Rectangle(5, 5, 300, 15));
            this.progressText = config.getColor("ui.progress_text", Color.BLACK);
            this.hideProgressText =  config.getBoolean("ui.hide_progress_text");
            this.minShowSeconds = config.getInt("ui.min_show_seconds", 5);
            this.progressBar = config.getColor("ui.progress_bar", 0x6699CC);
            this.status = config.getRect("ui.status", new Rectangle(5, 25, 500, 100));
            this.statusText = config.getColor("ui.status_text", Color.BLACK);
            this.textShadow = config.getColor("ui.text_shadow", Color.CLEAR);
            this.hideDecorations = config.getBoolean("ui.hide_decorations");
            this.backgroundImage = config.getString("ui.background_image");
            // default to black or white bg color, depending on the brightness of the progressText
            int defaultBackground = (0.5f < Color.brightness(this.progressText)) ?
                Color.BLACK : Color.WHITE;
            this.background = config.getColor("ui.background", defaultBackground);
            this.progressImage = config.getString("ui.progress_image");
            this.rotatingBackgrounds = stringsToList(
                config.getMultiValue("ui.rotating_background"));
            this.iconImages = stringsToList(config.getMultiValue("ui.icon"));
            this.errorBackground = config.getString("ui.error_background");

            // On an installation error, where do we point the user.
            String installError = config.getUrl("ui.install_error", null);
            this.installError = (installError == null) ?
                "m.default_install_error" : MessageUtil.taint(installError);

            // the patch notes bits
            this.patchNotes = config.getRect("ui.patch_notes", new Rectangle(5, 50, 112, 26));
            this.patchNotesUrl = config.getUrl("ui.patch_notes_url", null);

            // step progress percentage (defaults and then customized values)
            EnumMap<Step, List<Integer>> stepPercentages = new EnumMap<>(Step.class);
            for (Step step : Step.values()) {
                stepPercentages.put(step, step.defaultPercents);
            }
            for (UpdateInterface.Step step : UpdateInterface.Step.values()) {
                String spec = config.getString("ui.percents." + step.name());
                if (spec != null) {
                    try {
                        stepPercentages.put(step, intsToList(StringUtil.parseIntArray(spec)));
                    } catch (Exception e) {
                        log.warning("Failed to parse percentages for " + step + ": " + spec);
                    }
                }
            }
            this.stepPercentages = Collections.unmodifiableMap(stepPercentages);
        }
    }

    /**
     * Used by {@link #verifyMetadata} to communicate status in circumstances where it needs to
     * take network actions.
     */
    public interface StatusDisplay
    {
        /** Requests that the specified status message be displayed. */
        void updateStatus (String message);
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
     * Reads the {@code getdown.txt} config file into a {@code Config} object and returns it.
     */
    public static Config readConfig (EnvConfig envc, boolean checkPlatform) {
        Config config = null;
        File cfgfile = new File(envc.appDir, CONFIG_FILE);
        Config.ParseOpts opts = Config.createOpts(checkPlatform);
        try {
            // if we have a configuration file, read the data from it
            if (cfgfile.exists()) {
                config = Config.parseConfig(cfgfile, opts);
            }
            // otherwise, try reading data from our backup config file; thanks to funny windows
            // bullshit, we have to do this backup file fiddling in case we got screwed while
            // updating getdown.txt during normal operation
            else if ((cfgfile = new File(envc.appDir, Application.CONFIG_FILE + "_old")).exists()) {
                config = Config.parseConfig(cfgfile, opts);
            }
            // otherwise, issue a warning that we found no getdown file
            else {
                log.info("Found no getdown.txt file", "appdir", envc.appDir);
            }
        } catch (Exception e) {
            log.warning("Failure reading config file", "file", config, e);
        }

        // if we failed to read our config file, check for an appbase specified via a system
        // property; we can use that to bootstrap ourselves back into operation
        if (config == null) {
            log.info("Using 'appbase' from bootstrap config", "appbase", envc.appBase);
            Map<String, Object> cdata = new HashMap<>();
            cdata.put("appbase", envc.appBase);
            config = new Config(cdata);
        }

        return config;
    }

    /** A helper that is used to do HTTP downloads. This must be configured prior to using the
      * application instance. Yes this is a public mutable field, no I'm not going to create a
      * getter and setter just to pretend like that's not the case. */
    public Connector conn = Connector.DEFAULT;

    /**
     * Creates an application instance which records the location of the {@code getdown.txt}
     * configuration file from the supplied application directory.
     *
     */
    public Application (EnvConfig envc) {
        _envc = envc;
    }

    /**
     * Returns the configured application directory.
     */
    public File getAppDir () {
        return _envc.appDir;
    }

    /**
     * Returns whether the application should cache code resources prior to launching the
     * application.
     */
    public boolean useCodeCache ()
    {
        return _useCodeCache;
    }

    /**
     * Returns the number of days a cached code resource is allowed to stay unused before it
     * becomes eligible for deletion.
     */
    public int getCodeCacheRetentionDays ()
    {
        return _codeCacheRetentionDays;
    }

    /**
     * Returns the configured maximum concurrent downloads. Used to cap simultaneous downloads of
     * app files from its hosting server.
     */
    public int maxConcurrentDownloads () {
        return _maxConcDownloads;
    }

    /**
     * Returns a resource that refers to the application configuration file itself.
     */
    public Resource getConfigResource ()
    {
        try {
            return createResource(CONFIG_FILE, Resource.NORMAL);
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
     * Returns a list of strings (usually file paths relative to the app root dir) to add to the
     * classpath.
     */
    public List<String> getClassPathDirectories ()
    {
        return _cpdirs;
    }

    /**
     * Returns the digest of the given {@code resource}.
     */
    public String getDigest (Resource resource)
    {
        return _digest.getDigest(resource);
    }

    /**
     * Returns a list of the cleanup patterns used by application.
     */
    public List<String> cleanupPatterns() {
        return _cleanupPatterns;
    }

    /**
     * Returns a list of all the active {@link Resource} objects used by this application (code and
     * non-code).
     */
    public List<Resource> getAllActiveResources ()
    {
        List<Resource> allResources = new ArrayList<>();
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
        List<Resource> codes = new ArrayList<>(getCodeResources());
        for (AuxGroup aux : getAuxGroups()) {
            if (isAuxGroupActive(aux.name)) {
                codes.addAll(aux.codes);
            }
        }
        return codes;
    }

    /**
     * Returns all resources indicated to contain native library files (.dll, .so, etc.).
     */
    public List<Resource> getNativeResources ()
    {
        List<Resource> natives = new ArrayList<>();
        for (Resource resource: _resources) {
            if (resource.isNative()) {
                natives.add(resource);
            }
        }
        return natives;
    }

    /**
     * Returns all non-code resources and all resources from active auxiliary resource groups.
     */
    public List<Resource> getActiveResources ()
    {
        List<Resource> rsrcs = new ArrayList<>(getResources());
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
            URL remote = new URL(createVAppBase(_targetVersion), encodePath(pfile));
            return new Resource(pfile, remote, getLocalPath(pfile), Resource.NORMAL);
        } catch (Exception e) {
            log.warning("Failed to create patch resource path",
                "pfile", pfile, "appbase", _appbase, "tvers", _targetVersion, "error", e);
            return null;
        }
    }

    /**
     * @return directory into which a local VM installation should be unpacked.
     */
    public File getJavaLocalDir ()
    {
        return _javaLocalDir;
    }

    /**
     * @return a resource for a zip file containing a Java VM that can be downloaded to use in
     * place of the installed VM (in the case where the VM that launched Getdown does not meet the
     * application's version requirements) or null if no VM is available for this platform.
     */
    public Resource getJavaVMResource ()
    {
        if (StringUtil.isBlank(_javaLocation)) {
            return null;
        }

        // take extension from java location
        String vmfileExt = _javaLocation.substring(_javaLocation.lastIndexOf('.'));
        String vmfile = _javaLocalDir.getName() + vmfileExt;
        try {
            URL remote = new URL(createVAppBase(_targetVersion), encodePath(_javaLocation));
            return new Resource(vmfile, remote, getLocalPath(vmfile),
                                EnumSet.of(Resource.Attr.UNPACK, Resource.Attr.CLEAN));
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
            URL remote = new URL(createVAppBase(_targetVersion), encodePath(file));
            return new Resource(file, remote, getLocalPath(file), Resource.NORMAL);
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
            return _trackingURL == null ? null :
                HostWhitelist.verify(new URL(_trackingURL + encodePath(event + suffix + ga)));
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
     * @return a {@code Config} instance that contains information from the config file.
     *
     * @exception IOException thrown if there is an error reading the file or an error encountered
     * during its parsing.
     */
    public Config init (boolean checkPlatform) throws IOException
    {
        Config config = readConfig(_envc, checkPlatform);
        initBase(config);
        initJava(config);
        initTracking(config);
        initResources(config);
        initCleanupPatterns(config);
        initArgs(config);
        return config;
    }

    /**
     * Reads the basic config info from {@code config} into this instance. This includes things
     * like the appbase and version.
     */
    public void initBase (Config config) throws IOException {
        // first extract our version information
        _version = config.getLong("version", -1L);

        // determine our application base, this way if anything goes wrong later in the
        // process, our caller can use the appbase to download a new configuration file
        _appbase = config.getString("appbase");
        if (_appbase == null) {
            throw new RuntimeException("m.missing_appbase");
        }

        // check if we're overriding the domain in the appbase, and sub envvars
        _appbase = resolveEnvVars(SysProps.overrideAppbase(_appbase));

        // make sure there's a trailing slash
        if (!_appbase.endsWith("/")) {
            _appbase += "/";
        }

        // if we are a versioned deployment, create a versioned appbase
        try {
            _vappbase = createVAppBase(_version);
        } catch (MalformedURLException mue) {
            String err = MessageUtil.tcompose("m.invalid_appbase", _appbase);
            throw new IOException(err, mue);
        }

        // check for a latest config URL
        String latest = config.getString("latest");
        if (latest != null) {
            latest = processArg(latest);
            if (latest.startsWith(_appbase)) {
                latest = _appbase + latest.substring(_appbase.length());
            } else {
                latest = SysProps.replaceDomain(latest);
            }
            try {
                _latest = HostWhitelist.verify(new URL(latest));
            } catch (MalformedURLException mue) {
                log.warning("Invalid URL for latest attribute.", mue);
            }
        }

        // read some miscellaneous configurations
        _strictComments = config.getBoolean("strict_comments");
        _allowOffline = config.getBoolean("allow_offline");
        _revalidatePolicy = config.getEnum(
            "revalidate_policy", RevalidatePolicy.class, RevalidatePolicy.AFTER_UPDATE);
        int tpSize = SysProps.threadPoolSize();
        _maxConcDownloads = Math.max(1, config.getInt("max_concurrent_downloads", tpSize));
        _verifyTimeout = config.getInt("verify_timeout", 60);

        // whether to cache code resources and launch from cache
        _useCodeCache = config.getBoolean("use_code_cache");
        _codeCacheRetentionDays = config.getInt("code_cache_retention_days", 7);
    }

    /**
     * Reads the JVM requirements from {@code config} into this instance. This includes things like
     * the min and max java version, location of a locally installed JRE, etc.
     */
    public void initJava (Config config) {
        // check to see if we're using a custom java.version property and regex
        _javaVersionProp = config.getString("java_version_prop", _javaVersionProp);
        _javaVersionRegex = config.getString("java_version_regex", _javaVersionRegex);

        // check to see if we require a particular JVM version and have a supplied JVM
        _javaMinVersion = config.getLong("java_version", _javaMinVersion);
        // we support java_min_version as an alias of java_version; it better expresses the check
        // that's going on and better mirrors java_max_version
        _javaMinVersion = config.getLong("java_min_version", _javaMinVersion);
        // check to see if we require a particular max JVM version and have a supplied JVM
        _javaMaxVersion = config.getLong("java_max_version", _javaMaxVersion);
        // check to see if we require a particular JVM version and have a supplied JVM
        _javaExactVersionRequired = config.getBoolean("java_exact_version_required");

        _javaLocation = config.getString("java_location");

        // used only in conjunction with java_location
        _javaLocalDir = getLocalPath(config.getString("java_local_dir", LaunchUtil.LOCAL_JAVA_DIR));
    }

    /**
     * Reads the install tracking info from {@code config} into this instance.
     */
    public void initTracking (Config config) {
        // determine whether we have any tracking configuration
        _trackingURL = config.getString("tracking_url");

        // check for tracking progress percent configuration
        String trackPcts = config.getString("tracking_percents");
        if (!StringUtil.isBlank(trackPcts)) {
            _trackingPcts = new HashSet<>();
            for (int pct : StringUtil.parseIntArray(trackPcts)) {
                _trackingPcts.add(pct);
            }
        } else if (!StringUtil.isBlank(_trackingURL)) {
            _trackingPcts = new HashSet<>();
            _trackingPcts.add(50);
        }

        // Check for tracking cookie configuration
        _trackingCookieName = config.getString("tracking_cookie_name");
        _trackingCookieProperty = config.getString("tracking_cookie_property");

        // Some app may need an extra suffix added to the tracking URL
        _trackingURLSuffix = config.getString("tracking_url_suffix");

        // Some app may need to generate google analytics code
        _trackingGAHash = config.getString("tracking_ga_hash");
    }

    /**
     * Reads the app resource info from {@code config} into this instance.
     */
    public void initResources (Config config) throws IOException {
        // clear our arrays as we may be reinitializing
        _codes.clear();
        _resources.clear();
        _auxgroups.clear();

        // parse our code resources
        if (config.getMultiValue("code") == null &&
            config.getMultiValue("ucode") == null) {
            throw new IOException("m.missing_code");
        }
        parseResources(config, "code", Resource.NORMAL, _codes);
        parseResources(config, "ucode", Resource.UNPACK, _codes);

        // parse our non-code resources
        parseResources(config, "resource", Resource.NORMAL, _resources);
        parseResources(config, "uresource", Resource.UNPACK, _resources);
        parseResources(config, "xresource", Resource.EXEC, _resources);
        parseResources(config, "presource", Resource.PRELOAD, _resources);
        parseResources(config, "nresource", Resource.NATIVE, _resources);

        // parse our auxiliary resource groups
        for (String auxgroup : config.getList("auxgroups")) {
            List<Resource> codes = new ArrayList<>();
            parseResources(config, auxgroup + ".code", Resource.NORMAL, codes);
            parseResources(config, auxgroup + ".ucode", Resource.UNPACK, codes);
            List<Resource> rsrcs = new ArrayList<>();
            parseResources(config, auxgroup + ".resource", Resource.NORMAL, rsrcs);
            parseResources(config, auxgroup + ".xresource", Resource.EXEC, rsrcs);
            parseResources(config, auxgroup + ".uresource", Resource.UNPACK, rsrcs);
            parseResources(config, auxgroup + ".presource", Resource.PRELOAD, rsrcs);
            parseResources(config, auxgroup + ".nresource", Resource.NATIVE, rsrcs);
            _auxgroups.put(auxgroup, new AuxGroup(auxgroup, codes, rsrcs));
        }
    }

    /**
     * Reads the cleanup patterns from {@code config} into this instance.
     */
    public void initCleanupPatterns (Config config) {
        // clear our arrays as we may be reinitializing
        _cleanupPatterns.clear();

        // parse cleanup patterns
        String[] patterns = config.getMultiValue("cleanup_pattern");
        if (patterns == null) {
            return;
        }

        _cleanupPatterns.addAll(Arrays.asList(patterns));
    }

    /**
     * Reads the command line arg info from {@code config} into this instance.
     */
    public void initArgs (Config config) throws IOException {
        _jvmargs.clear();
        _appargs.clear();
        _txtJvmArgs.clear();
        _cpdirs.clear();

        String appPrefix = _envc.appId == null ? "" : (_envc.appId + ".");

        // determine our application class name (use app-specific class _if_ one is provided)
        _class = config.getString("class");
        if (appPrefix.length() > 0) {
            _class = config.getString(appPrefix + "class", _class);
        }
        if (_class == null) {
            throw new IOException("m.missing_class");
        }

        // transfer our JVM arguments (we include both "global" args and app_id-prefixed args)
        addAll(config.getMultiValue("jvmarg"), _jvmargs);
        if (appPrefix.length() > 0) {
            addAll(config.getMultiValue(appPrefix + "jvmarg"), _jvmargs);
        }

        // get the set of optimum JVM arguments
        _optimumJvmArgs = config.getMultiValue("optimum_jvmarg");

        // transfer our application arguments
        addAll(config.getMultiValue(appPrefix + "apparg"), _appargs);

        // add the launch specific application arguments
        _appargs.addAll(_envc.appArgs);

        // look for custom arguments
        fillAssignmentListFromPairs("extra.txt", _txtJvmArgs);

        // add any extra classpath entries
        addAll(config.getMultiValue(appPrefix + "classpath"), _cpdirs);

        // extract some info used to configure our child process on macOS
        _dockName = config.getString("ui.name");
        _dockIconPath = config.getString("ui.mac_dock_icon", "../desktop.icns");
    }

    /**
     * Adds strings of the form pair0=pair1 to collector for each pair parsed out of pairLocation.
     */
    protected void fillAssignmentListFromPairs (String pairLocation, List<String> collector)
    {
        File pairFile = getLocalPath(pairLocation);
        if (pairFile.exists()) {
            try {
                List<String[]> args = Config.parsePairs(pairFile, Config.createOpts(false));
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
    public URL getRemoteURL (String path) throws MalformedURLException
    {
        return new URL(_vappbase, encodePath(path));
    }

    /**
     * Returns the local path to the specified resource.
     */
    public File getLocalPath (String path)
    {
        return new File(getAppDir(), path);
    }

    /**
     * Returns true if we either have no version requirement, are running in a JVM that meets our
     * version requirements or have what appears to be a version of the JVM that meets our
     * requirements.
     */
    public boolean haveValidJavaVersion ()
    {
        // if we're doing no version checking, then yay!
        if (_javaMinVersion == 0 && _javaMaxVersion == 0) return true;

        try {
            // parse the version out of the java.version (or custom) system property
            long version = SysProps.parseJavaVersion(_javaVersionProp, _javaVersionRegex);

            log.info("Checking Java version", "current", version,
                     "wantMin", _javaMinVersion, "wantMax", _javaMaxVersion);

            // if we have an unpacked VM, check the 'release' file for its version
            Resource vmjar = getJavaVMResource();
            if (vmjar != null && vmjar.isMarkedValid()) {
                File relfile = new File(_javaLocalDir, "release");
                if (!relfile.exists()) {
                    log.warning("Unpacked JVM missing 'release' file. Assuming valid version.");
                    return true;
                }

                long vmvers = VersionUtil.readReleaseVersion(relfile, _javaVersionRegex);
                if (vmvers == 0L) {
                    log.warning("Unable to read version from 'release' file. Assuming valid.");
                    return true;
                }

                version = vmvers;
                log.info("Checking version of unpacked JVM [vers=" + version + "].");
            }

            if (_javaExactVersionRequired) {
                if (version == _javaMinVersion) return true;
                else {
                    log.warning("An exact Java VM version is required.", "current", version,
                                "required", _javaMinVersion);
                    return false;
                }
            }

            boolean minVersionOK = (_javaMinVersion == 0) || (version >= _javaMinVersion);
            boolean maxVersionOK = (_javaMaxVersion == 0) || (version <= _javaMaxVersion);
            return minVersionOK && maxVersionOK;

        } catch (RuntimeException re) {
            // if we can't parse the java version we're in weird land and should probably just try
            // our luck with what we've got rather than try to download a new jvm
            log.warning("Unable to parse VM version, hoping for the best",
                        "error", re, "needed", _javaMinVersion);
            return true;
        }
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
     * Attempts to redownload the {@code getdown.txt} file based on information parsed from a
     * previous call to {@link #init}.
     */
    public void attemptRecovery (StatusDisplay status)
        throws IOException
    {
        status.updateStatus("m.updating_metadata");
        downloadConfigFile();
    }

    /**
     * Downloads and replaces the {@code getdown.txt} and {@code digest.txt} files with
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
            throw new IOException(err, mue);
        }

        try {
            // now re-download our control files; we download the digest first so that if it fails,
            // our config file will still reference the old version and re-running the updater will
            // start the whole process over again
            downloadDigestFiles();
            downloadConfigFile();

        } catch (IOException ex) {
            // if we are allowing offline execution, we want to allow the application to run in its
            // current form rather than aborting the entire process; to do this, we delete the
            // version.txt file and "trick" Getdown into thinking that it just needs to validate
            // the application as is; next time the app runs when connected to the internet, it
            // will have to rediscover that it needs updating and reattempt to update itself
            if (_allowOffline) {
                log.warning("Failed to update digest files.  Attempting offline operaton.", ex);
                if (!FileUtil.deleteHarder(getLocalPath(VERSION_FILE))) {
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
        ArrayList<String> args = new ArrayList<>();

        // reconstruct the path to the JVM
        args.add(LaunchUtil.getJVMBinaryPath(_javaLocalDir, SysProps.debug() || optimum));

        // check whether we're using -jar mode or -classpath mode
        boolean dashJarMode = MANIFEST_CLASS.equals(_class);

        // add the -classpath arguments if we're not in -jar mode
        ClassPath classPath = PathBuilder.buildClassPath(this);
        if (!dashJarMode) {
            args.add("-classpath");
            args.add(classPath.asArgumentString(getAppDir()));
        }

        // we love our Mac users, so we do nice things to preserve our application identity
        if (LaunchUtil.isMacOS()) {
            args.add("-Xdock:icon=" + getLocalPath(_dockIconPath).getAbsolutePath());
            args.add("-Xdock:name=" + _dockName);
        }

        // forward our proxy settings
        conn.addProxyArgs(args);

        // add the marker indicating the app is running in getdown
        args.add("-D" + Properties.GETDOWN + "=true");

        // set the native library path if we have native resources
        // @TODO optional getdown.txt parameter to set addCurrentLibraryPath to true or false?
        ClassPath javaLibPath = PathBuilder.buildLibsPath(this, true);
        if (javaLibPath != null) {
            args.add("-Djava.library.path=" + javaLibPath.asArgumentString(getAppDir()));
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

        // if we're in -jar mode add those arguments, otherwise add the app class name
        if (dashJarMode) {
            args.add("-jar");
            args.add(classPath.asArgumentString(getAppDir()));
        } else {
            args.add(_class);
        }

        // finally add the application arguments
        for (String string : _appargs) {
            args.add(processArg(string));
        }

        String[] envp = createEnvironment();
        String[] sargs = args.toArray(new String[args.size()]);
        log.info("Running " + StringUtil.join(sargs, "\n  "));

        return Runtime.getRuntime().exec(sargs, envp, getAppDir());
    }

    /**
     * If the application provided environment variables, combine those with the current
     * environment and return that in a style usable for {@link Runtime#exec(String, String[])}.
     * If the application didn't provide any environment variables, null is returned to just use
     * the existing environment.
     */
    protected String[] createEnvironment ()
    {
        List<String> envvar = new ArrayList<>();
        fillAssignmentListFromPairs("env.txt", envvar);
        if (envvar.isEmpty()) {
            log.info("Didn't find any custom environment variables, not setting any.");
            return null;
        }

        List<String> envAssignments = new ArrayList<>();
        for (String assignment : envvar) {
            envAssignments.add(processArg(assignment));
        }
        for (Map.Entry<String, String> environmentEntry : System.getenv().entrySet()) {
            envAssignments.add(environmentEntry.getKey() + "=" + environmentEntry.getValue());
        }
        String[] envp = envAssignments.toArray(new String[envAssignments.size()]);
        log.info("Environment " + StringUtil.join(envp, "\n "));
        return envp;
    }

    /**
     * Runs this application directly in the current VM.
     */
    public void invokeDirect () throws IOException
    {
        ClassPath classPath = PathBuilder.buildClassPath(this);
        URL[] jarUrls = classPath.asUrls();

        // create custom class loader
        URLClassLoader loader = new URLClassLoader(jarUrls, ClassLoader.getSystemClassLoader()) {
            @Override protected PermissionCollection getPermissions (CodeSource code) {
                Permissions perms = new Permissions();
                perms.add(new AllPermission());
                return perms;
            }
        };
        Thread.currentThread().setContextClassLoader(loader);

        log.info("Configured URL class loader:");
        for (URL url : jarUrls) log.info("  " + url);

        // configure any system properties that we can
        for (String jvmarg : _jvmargs) {
            if (jvmarg.startsWith("-D")) {
                jvmarg = processArg(jvmarg.substring(2));
                int eqidx = jvmarg.indexOf('=');
                if (eqidx == -1) {
                    log.warning("Bogus system property: '" + jvmarg + "'?");
                } else {
                    System.setProperty(jvmarg.substring(0, eqidx), jvmarg.substring(eqidx+1));
                }
            }
        }

        // pass along any pass-through arguments
        Map<String, String> passProps = new HashMap<>();
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

        // prepare our app arguments
        String[] args = new String[_appargs.size()];
        for (int ii = 0; ii < args.length; ii++) args[ii] = processArg(_appargs.get(ii));

        try {
            log.info("Loading " + _class);
            Class<?> appclass = loader.loadClass(_class);
            Method main = appclass.getMethod("main", EMPTY_STRING_ARRAY.getClass());
            log.info("Invoking main({" + StringUtil.join(args, ", ") + "})");
            main.invoke(null, new Object[] { args });
        } catch (Exception e) {
            log.warning("Failure invoking app main", e);
        }
    }

    /** Replaces the application directory, version and env vars in any argument. */
    protected String processArg (String arg)
    {
        arg = arg.replace("%APPDIR%", getAppDir().getAbsolutePath());
        arg = arg.replace("%VERSION%", String.valueOf(_version));
        arg = resolveEnvVars(arg);
        return arg;
    }

    /** Resolves env var substitutions in {@code text}. */
    protected String resolveEnvVars (String text) {
        // if the text contains %ENV.FOO% replace it with FOO looked up in the environment
        if (text.contains(ENV_VAR_PREFIX)) {
            StringBuffer sb = new StringBuffer();
            Matcher matcher = ENV_VAR_PATTERN.matcher(text);
            while (matcher.find()) {
                String varName = matcher.group(1), varValue = System.getenv(varName);
                String repValue = varValue == null ? "MISSING-"+varName : varValue;
                matcher.appendReplacement(sb, Matcher.quoteReplacement(repValue));
            }
            matcher.appendTail(sb);
            return sb.toString();
        } else {
            return text;
        }
    }

    /**
     * Loads the {@code digest.txt} file and verifies the contents of both that file and the
     * {@code getdown.text} file. Then it loads the {@code version.txt} and decides
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

        // this will read in the contents of the digest file and validate itself
        try {
            _digest = new Digest(getAppDir(), _strictComments);
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
                downloadDigestFiles();
                _digest = new Digest(getAppDir(), _strictComments);
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
            downloadDigestFiles();
            _digest = new Digest(getAppDir(), _strictComments);
        }

        // now verify the contents of our main config file
        Resource crsrc = getConfigResource();
        if (!_digest.validateResource(crsrc, null)) {
            status.updateStatus("m.updating_metadata");
            // attempt to redownload both of our metadata files; again we pass errors up to our
            // caller because there's nothing we can do to automatically recover
            downloadConfigFile();
            downloadDigestFiles();
            _digest = new Digest(getAppDir(), _strictComments);
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
                try {
                    List<String[]> vdata = Config.parsePairs(
                        new StringReader(conn.fetch(_latest)), Config.createOpts(false));
                    for (String[] pair : vdata) {
                        if ("version".equals(pair[0])) {
                            _targetVersion = Math.max(Long.parseLong(pair[1]), _targetVersion);
                            if (fileVersion != -1 && _targetVersion > fileVersion) {
                                // replace the file with the newest version
                                try (FileOutputStream fos = new FileOutputStream(vfile);
                                     PrintStream out = new PrintStream(fos)) {
                                    out.println(_targetVersion);
                                }
                            }
                            break;
                        }
                    }
                } catch (Exception e) {
                    log.warning("Unable to retrieve version from latest config file.", e);
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
     * @param obs a progress observer that will be notified of verification progress. NOTE: this
     * observer may be called from arbitrary threads, so if you update a UI based on calls to it,
     * you have to take care to get back to your UI thread.
     * @param alreadyValid if non-null a 1 element array that will have the number of "already
     * validated" resources filled in.
     * @param unpacked a set to populate with unpacked resources.
     * @param toInstall a list into which to add resources that need to be installed.
     * @param toDownload a list into which to add resources that need to be downloaded.
     */
    public void verifyResources (
        ProgressObserver obs, int[] alreadyValid, Set<Resource> unpacked,
        Set<Resource> toInstall, Set<Resource> toDownload)
        throws InterruptedException
    {
        // resources are verified on background threads supplied by the thread pool, and progress
        // is reported by posting runnable actions to the actions queue which is processed by the
        // main (UI) thread
        ExecutorService exec = Executors.newFixedThreadPool(SysProps.threadPoolSize());
        final BlockingQueue<Runnable> actions = new LinkedBlockingQueue<>();
        final int[] completed = new int[1];

        long start = System.currentTimeMillis();

        // obtain the sizes of the resources to validate
        List<Resource> rsrcs = getAllActiveResources();
        long[] sizes = new long[rsrcs.size()];
        long totalSize = 0;
        for (int ii = 0; ii < sizes.length; ii++) {
            totalSize += sizes[ii] = rsrcs.get(ii).getLocal().length();
        }
        final ProgressObserver fobs = obs;
        // as long as we forward aggregated progress updates to the UI thread, having multiple
        // threads update a progress aggregator is "mostly" thread-safe
        final ProgressAggregator pagg = new ProgressAggregator(new ProgressObserver() {
            public void progress (final int percent) {
                actions.add(new Runnable() {
                    public void run () {
                        fobs.progress(percent);
                    }
                });
            }
        }, sizes);

        final int[] fAlreadyValid = alreadyValid;
        final Set<Resource> toInstallAsync = new ConcurrentSkipListSet<>(toInstall);
        final Set<Resource> toDownloadAsync = new ConcurrentSkipListSet<>();
        final Set<Resource> unpackedAsync = new ConcurrentSkipListSet<>();

        for (int ii = 0; ii < sizes.length; ii++) {
            final Resource rsrc = rsrcs.get(ii);
            final int index = ii;
            exec.execute(new Runnable() {
                public void run () {
                    verifyResource(rsrc, pagg.startElement(index), fAlreadyValid,
                                   unpackedAsync, toInstallAsync, toDownloadAsync);
                    actions.add(new Runnable() {
                        public void run () {
                            completed[0] += 1;
                        }
                    });
                }
            });
        }

        while (completed[0] < rsrcs.size()) {
            // we should be getting progress completion updates WAY more often than one every
            // minute, so if things freeze up for 60 seconds, abandon ship
            Runnable action = actions.poll(_verifyTimeout, TimeUnit.SECONDS);
            if (action == null) {
                throw new IllegalStateException("m.verify_timeout");
            }
            action.run();
        }

        exec.shutdown();

        toInstall.addAll(toInstallAsync);
        toDownload.addAll(toDownloadAsync);
        unpacked.addAll(unpackedAsync);

        long complete = System.currentTimeMillis();
        log.info("Verified resources", "count", rsrcs.size(), "alreadyValid", alreadyValid[0],
                 "size", (totalSize/1024) + "k", "duration", (complete-start) + "ms");
    }

    private void verifyResource (Resource rsrc, ProgressObserver obs, int[] alreadyValid,
                                 Set<Resource> unpacked,
                                 Set<Resource> toInstall, Set<Resource> toDownload) {
        if (_revalidatePolicy != RevalidatePolicy.ALWAYS && rsrc.isMarkedValid()) {
            if (alreadyValid != null) {
                alreadyValid[0]++;
            }
            obs.progress(100);
            return;
        }

        try {
            if (_digest.validateResource(rsrc, obs)) {
                // if the resource has a _new file, add it to to-install list
                if (rsrc.getLocalNew().exists()) {
                    toInstall.add(rsrc);
                    return;
                }
                rsrc.applyAttrs();
                unpacked.add(rsrc);
                rsrc.markAsValid();
                return;
            }

        } catch (Exception e) {
            log.info("Failure verifying resource. Requesting redownload...",
                     "rsrc", rsrc, "error", e);

        } finally {
            obs.progress(100);
        }
        toDownload.add(rsrc);
    }

    /**
     * Unpacks the resources that require it (we know that they're valid).
     *
     * @param unpacked a set of resources to skip because they're already unpacked.
     */
    public void unpackResources (ProgressObserver obs, Set<Resource> unpacked) {
        List<Resource> rsrcs = getActiveResources();

        // remove resources that we don't want to unpack
        for (Iterator<Resource> it = rsrcs.iterator(); it.hasNext(); ) {
            Resource rsrc = it.next();
            if (!rsrc.shouldUnpack() || unpacked.contains(rsrc)) {
                it.remove();
            }
        }

        // obtain the sizes of the resources to unpack
        long[] sizes = new long[rsrcs.size()];
        for (int ii = 0; ii < sizes.length; ii++) {
            sizes[ii] = rsrcs.get(ii).getLocal().length();
        }

        ProgressAggregator pagg = new ProgressAggregator(obs, sizes);
        for (int ii = 0; ii < sizes.length; ii++) {
            Resource rsrc = rsrcs.get(ii);
            ProgressObserver pobs = pagg.startElement(ii);
            try {
                rsrc.unpack();
            } catch (IOException ioe) {
                log.warning("Failure unpacking resource", "rsrc", rsrc, ioe);
            }
            pobs.progress(100);
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
        String url = version < 0 ? _appbase : _appbase.replace("%VERSION%", String.valueOf(version));
        return HostWhitelist.verify(new URL(url));
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
    protected void downloadConfigFile () throws IOException
    {
        downloadControlFile(CONFIG_FILE, 0);
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
     * Downloads the digest files and validates their signature.
     * @throws IOException
     */
    protected void downloadDigestFiles ()
        throws IOException
    {
        for (int version = 1; version <= Digest.VERSION; version++) {
            downloadControlFile(Digest.digestFile(version), version);
        }
    }

    /**
     * Downloads a new copy of the specified control file, optionally validating its signature.
     * If the download is successful, moves it over the old file on the filesystem.
     *
     * <p> TODO: Switch to PKCS #7 or CMS.
     *
     * @param sigVersion if {@code 0} no validation will be performed, if {@code > 0} then this
     * should indicate the version of the digest file being validated which indicates which
     * algorithm to use to verify the signature. See {@link Digest#VERSION}.
     */
    protected void downloadControlFile (String path, int sigVersion)
        throws IOException
    {
        File target = downloadFile(path);

        if (sigVersion > 0) {
            if (_envc.certs.isEmpty()) {
                log.info("No signing certs, not verifying digest.txt", "path", path);

            } else {
                File signatureFile = downloadFile(path + SIGNATURE_SUFFIX);
                byte[] signature = null;
                try (FileInputStream signatureStream = new FileInputStream(signatureFile)) {
                    signature = StreamUtil.toByteArray(signatureStream);
                } finally {
                    FileUtil.deleteHarder(signatureFile); // delete the file regardless
                }

                byte[] buffer = new byte[8192];
                int length, validated = 0;
                for (Certificate cert : _envc.certs) {
                    try (FileInputStream dataInput = new FileInputStream(target)) {
                        Signature sig = Signature.getInstance(Digest.sigAlgorithm(sigVersion));
                        sig.initVerify(cert);
                        while ((length = dataInput.read(buffer)) != -1) {
                            sig.update(buffer, 0, length);
                        }

                        if (!sig.verify(Base64.decode(signature, Base64.DEFAULT))) {
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

                    }
                }

                // if we couldn't find a key that validates our digest, we are the hosed!
                if (validated == 0) {
                    // delete the temporary digest file as we know it is invalid
                    FileUtil.deleteHarder(target);
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
    protected File downloadFile (String path) throws IOException
    {
        File target = getLocalPath(path + "_new");

        URL targetURL = null;
        try {
            targetURL = getRemoteURL(path);
        } catch (Exception e) {
            log.warning("Requested to download invalid control file",
                "appbase", _vappbase, "path", path, "error", e);
            throw new IOException("Invalid path '" + path + "'.", e);
        }

        log.info("Attempting to refetch '" + path + "' from '" + targetURL + "'.");
        conn.download(targetURL, target); // stream the URL into our temporary file
        return target;
    }

    /** Helper function for creating {@link Resource} instances. */
    protected Resource createResource (String path, EnumSet<Resource.Attr> attrs)
        throws MalformedURLException
    {
        return new Resource(path, getRemoteURL(path), getLocalPath(path), attrs);
    }

    /** Helper function to add all values in {@code values} (if non-null) to {@code target}. */
    protected static void addAll (String[] values, List<String> target) {
        if (values != null) {
            Collections.addAll(target, values);
        }
    }

    /**
     * Make an immutable List from the specified int array.
     */
    public static List<Integer> intsToList (int[] values)
    {
        List<Integer> list = new ArrayList<>(values.length);
        for (int val : values) {
            list.add(val);
        }
        return Collections.unmodifiableList(list);
    }

    /**
     * Make an immutable List from the specified String array.
     */
    public static List<String> stringsToList (String[] values)
    {
        return values == null ? null : Collections.unmodifiableList(Arrays.asList(values));
    }

    /** Used to parse resources with the specified name. */
    protected void parseResources (Config config, String name, EnumSet<Resource.Attr> attrs,
                                   List<Resource> list)
    {
        String[] rsrcs = config.getMultiValue(name);
        if (rsrcs == null) {
            return;
        }
        for (String rsrc : rsrcs) {
            try {
                list.add(createResource(rsrc, attrs));
            } catch (Exception e) {
                log.warning("Invalid resource '" + rsrc + "'. " + e);
            }
        }
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
            int low = 100000000, high = 1000000000;
            _trackingId = low + _rando.nextInt(high-low);
        }
        StringBuilder cookie = new StringBuilder("&utmcc=__utma%3D").append(_trackingGAHash);
        cookie.append(".").append(_trackingId);
        cookie.append(".").append(_trackingStart).append(".").append(_trackingStart);
        cookie.append(".").append(time).append(".1%3B%2B");
        cookie.append("__utmz%3D").append(_trackingGAHash).append(".");
        cookie.append(_trackingStart).append(".1.1.");
        cookie.append("utmcsr%3D(direct)%7Cutmccn%3D(direct)%7Cutmcmd%3D(none)%3B");
        int low = 1000000000, high = 2000000000;
        cookie.append("&utmn=").append(_rando.nextInt(high-low));
        return cookie.toString();
    }

    /**
     * Encodes a path for use in a URL.
     */
    protected static String encodePath (String path)
    {
        try {
            // we want to keep slashes because we're encoding an entire path; also we need to turn
            // + into %20 because web servers don't like + in paths or file names, blah
            return URLEncoder.encode(path, "UTF-8").replace("%2F", "/").replace("+", "%20");
        } catch (UnsupportedEncodingException ue) {
            log.warning("Failed to URL encode " + path + ": " + ue);
            return path;
        }
    }

    protected final EnvConfig _envc;
    protected Digest _digest;

    protected long _version = -1;
    protected long _targetVersion = -1;
    protected String _appbase;
    protected URL _vappbase;
    protected URL _latest;
    protected String _class;
    protected String _dockName;
    protected String _dockIconPath;
    protected boolean _strictComments;
    protected boolean _allowOffline;
    protected int _maxConcDownloads;

    protected String _trackingURL;
    protected Set<Integer> _trackingPcts;
    protected String _trackingCookieName;
    protected String _trackingCookieProperty;
    protected String _trackingURLSuffix;
    protected String _trackingGAHash;
    protected long _trackingStart;
    protected int _trackingId;

    protected String _javaVersionProp = "java.version";
    protected String _javaVersionRegex = "(\\d+)(?:\\.(\\d+)(?:\\.(\\d+)([_.]\\d+)?)?)?";
    protected long _javaMinVersion, _javaMaxVersion;
    protected boolean _javaExactVersionRequired;
    protected String _javaLocation;
    protected File _javaLocalDir;

    protected final List<Resource> _codes = new ArrayList<>();
    protected final List<Resource> _resources = new ArrayList<>();
    protected final List<String> _cleanupPatterns = new ArrayList<>();
    protected final List<String> _cpdirs = new ArrayList<>();

    protected int _verifyTimeout = 60;

    protected RevalidatePolicy _revalidatePolicy = RevalidatePolicy.AFTER_UPDATE;
    protected boolean _useCodeCache;
    protected int _codeCacheRetentionDays;

    protected final Map<String,AuxGroup> _auxgroups = new HashMap<>();
    protected final Map<String,Boolean> _auxactive = new HashMap<>();

    protected final List<String> _jvmargs = new ArrayList<>();
    protected final List<String> _appargs = new ArrayList<>();

    protected String[] _optimumJvmArgs;

    protected final List<String> _txtJvmArgs = new ArrayList<>();

    /** Locks gettingdown.lock in the app dir. Held the entire time updating is going on.*/
    protected FileLock _lock;

    /** Channel to the file underlying _lock.  Kept around solely so the lock doesn't close. */
    protected FileChannel _lockChannel;

    protected final Random _rando = new Random();

    protected static final String[] EMPTY_STRING_ARRAY = new String[0];

    protected static final String ENV_VAR_PREFIX = "%ENV.";
    protected static final Pattern ENV_VAR_PATTERN = Pattern.compile("%ENV\\.(.*?)%");

    protected static enum RevalidatePolicy { ALWAYS, AFTER_UPDATE }
}
