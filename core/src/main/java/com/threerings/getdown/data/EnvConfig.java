//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2016 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.data;

import java.io.File;
import java.io.FileInputStream;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;

import com.threerings.getdown.util.StringUtil;

/** Configuration that comes from our "environment" (command line args, sys props, etc.). */
public final class EnvConfig {

    /** Used to report problems or feedback by {@link #create}. */
    public static final class Note {
        public static enum Level { INFO, WARN, ERROR };
        public static Note info (String msg) { return new Note(Level.INFO, msg); }
        public static Note warn (String msg) { return new Note(Level.WARN, msg); }
        public static Note error (String msg) { return new Note(Level.ERROR, msg); }
        public final Level level;
        public final String message;
        public Note (Level level, String message) {
            this.level = level;
            this.message = message;
        }
    }

    /**
     * Creates an environment config, obtaining information (in order) from the following sources:
     *
     * <ul>
     * <li> A {@code bootstrap.properties} file bundled with the jar. </li>
     * <li> System properties supplied to the JVM. </li>
     * <li> The supplied command line arguments ({@code argv}). </li>
     * </ul>
     *
     * If a later source supplies a configuration already provided by a prior source, a warning
     * message will be logged to indicate the conflict, and the prior source will be used.
     *
     * @param notes a list into which notes are added, to be logged after the logging system has
     * been initialized (which cannot happen until the appdir is known). If any {@code ERROR} notes
     * are included, the app should terminate after reporting them.
     * @return an env config instance, or {@code null} if no appdir could be located via any
     * configuration source.
     */
    public static EnvConfig create (String[] argv, List<Note> notes) {
        String appDir = null, appDirProv = null;
        String appId = null, appIdProv = null;
        String appBase = null, appBaseProv = null;

        // start with bootstrap.properties config, if avaialble
        try {
            ResourceBundle bundle = ResourceBundle.getBundle("bootstrap");
            if (bundle.containsKey("appdir")) {
                appDir = bundle.getString("appdir");
                appDir = appDir.replace(USER_HOME_KEY, System.getProperty("user.home"));
                File appDirFile = new File(appDir);
                if (!appDirFile.exists()) {
                    appDirFile.mkdirs();
                }
                appDirProv = "bootstrap.properties";
            }
            if (bundle.containsKey("appid")) {
                appId = bundle.getString("appid");
                appIdProv = "bootstrap.properties";
            }
            if (bundle.containsKey("appbase")) {
                appBase = bundle.getString("appbase");
                appBaseProv = "bootstrap.properties";
            }
        } catch (MissingResourceException e) {
            // bootstrap.properties is optional; no need for a warning
        }

        // next seek config from system properties
        String spropsAppDir = SysProps.appDir();
        if (!StringUtil.isBlank(spropsAppDir)) {
            if (appDir == null) {
                appDir = spropsAppDir;
                appDirProv = "system property";
            } else {
                notes.add(Note.warn("Ignoring 'appdir' system property, have appdir via '" +
                                    appDirProv + "'"));
            }
        }
        String spropsAppId = SysProps.appId();
        if (!StringUtil.isBlank(spropsAppId)) {
            if (appId == null) {
                appId = spropsAppId;
                appIdProv = "system property";
            } else {
                notes.add(Note.warn("Ignoring 'appid' system property, have appid via '" +
                                    appIdProv + "'"));
            }
        }
        String spropsAppBase = SysProps.appBase();
        if (!StringUtil.isBlank(spropsAppBase)) {
            if (appBase == null) {
                appBase = spropsAppBase;
                appBaseProv = "system property";
            } else {
                notes.add(Note.warn("Ignoring 'appbase' system property, have appbase via '" +
                                    appBaseProv + "'"));
            }
        }

        // finally obtain config from command line arguments
        String argvAppDir = argv.length > 0 ? argv[0] : null;
        if (!StringUtil.isBlank(argvAppDir)) {
            if (appDir == null) {
                appDir = argvAppDir;
                appDirProv = "command line";
            } else {
                notes.add(Note.warn("Ignoring 'appdir' command line arg, have appdir via '" +
                                    appDirProv + "'"));
            }
        }
        String argvAppId = argv.length > 1 ? argv[1] : null;
        if (!StringUtil.isBlank(spropsAppId)) {
            if (appId == null) {
                appId = argvAppId;
                appIdProv = "command line";
            } else {
                notes.add(Note.warn("Ignoring 'appid' command line arg, have appid via '" +
                                    appIdProv + "'"));
            }
        }

        // ensure that we were able to fine an app dir
        if (appDir == null) {
            return null; // caller will report problem to user
        }

        // ensure that the appdir refers to a directory that exists
        File appDirFile = new File(appDir);
        if (!appDirFile.exists() || !appDirFile.isDirectory()) {
            notes.add(Note.error("Invalid appdir '" + appDir + "'."));
            return null;
        }

        notes.add(Note.info("Using appdir from " + appDirProv + ": " + appDir));
        if (appId != null) notes.add(Note.info("Using appid from " + appIdProv + ": " + appId));
        if (appBase != null) notes.add(Note.info("Using appbase from " + appBaseProv + ": " +
                                                 appBase));

        // pass along anything after the first two args as extra app args
        List<String> appArgs = argv.length > 2 ?
            Arrays.asList(argv).subList(2, argv.length) :
            Collections.<String>emptyList();

        // load X.509 certificate if it exists
        File crtFile = new File(appDirFile, Digest.digestFile(Digest.VERSION) + ".crt");
        List<Certificate> certs = new ArrayList<>();
        if (crtFile.exists()) {
            try (FileInputStream fis = new FileInputStream(crtFile)) {
                X509Certificate certificate = (X509Certificate)
                    CertificateFactory.getInstance("X.509").generateCertificate(fis);
                certs.add(certificate);
            } catch (Exception e) {
                notes.add(Note.error("Certificate error: " + e.getMessage()));
            }
        }

        return new EnvConfig(appDirFile, appId, appBase, certs, appArgs);
    }

    /** The directory in which the application and metadata is stored. */
    public final File appDir;

    /** Either {@code null} or an identifier for a secondary application that should be
      * launched. That app will use {@code appid.class} and {@code appid.apparg} to configure
      * itself but all other parameters will be the same as the primary app. */
    public final String appId;

    /** Either {@code null} or fallback {@code appbase} to use if one cannot be read from a
      * {@code getdown.txt} file during startup. */
    public final String appBase;

    /** Zero or more signing certificates used to verify the digest file. */
    public final List<Certificate> certs;

    /** Additional arguments to pass on to launched application. These will be added after the
      * args in the getdown.txt file. */
    public final List<String> appArgs;

    public EnvConfig (File appDir) {
        this(appDir, null, null, Collections.<Certificate>emptyList(),
             Collections.<String>emptyList());
    }

    private EnvConfig (File appDir, String appId, String appBase, List<Certificate> certs,
                       List<String> appArgs) {
        this.appDir = appDir;
        this.appId = appId;
        this.appBase = appBase;
        this.certs = certs;
        this.appArgs = appArgs;
    }

    private static final String USER_HOME_KEY = "${user.home}";
}
