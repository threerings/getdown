//
// $Id$

package com.threerings.getdown.data;

import java.awt.Color;
import java.awt.Rectangle;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.net.MalformedURLException;
import java.net.URL;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.samskivert.io.NestableIOException;
import com.samskivert.io.StreamUtil;
import com.samskivert.text.MessageUtil;
import com.samskivert.util.RunAnywhere;
import com.samskivert.util.StringUtil;

import org.apache.commons.io.CopyUtils;

import com.threerings.getdown.Log;
import com.threerings.getdown.util.ConfigUtil;
import com.threerings.getdown.util.LaunchUtil;
import com.threerings.getdown.util.MetaProgressObserver;
import com.threerings.getdown.util.ProgressObserver;

/**
 * Parses and provide access to the information contained in the
 * <code>getdown.txt</code> configuration file.
 */
public class Application
{
    /** The name of our configuration file. */
    public static final String CONFIG_FILE = "getdown.txt";

    /** The name of our target version file. */
    public static final String VERSION_FILE = "version.txt";

    /** System properties that are prefixed with this string will be
     * passed through to our application (minus this prefix). */
    public static final String PROP_PASSTHROUGH_PREFIX = "app.";

    /** Used to communicate information about the UI displayed when
     * updating the application. */
    public static class UpdateInterface
    {
        /** The human readable name of this application. */
        public String name;

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

        /** The path (relative to the appdir) to the background image. */
        public String background;

        /** The color of the text shadow. */
        public Color textShadow;
    }

    /** Used by {@link #verifyMetadata} to communicate status in
     * circumstances where it needs to take network actions. */
    public static interface StatusDisplay
    {
        /** Requests that the specified status message be displayed. */
        public void updateStatus (String message);
    }

    /**
     * Creates an application instance which records the location of the
     * <code>getdown.txt</code> configuration file from the supplied
     * application directory.
     */
    public Application (File appdir)
    {
        _appdir = appdir;
        _config = getLocalPath(CONFIG_FILE);
    }

    /**
     * Returns a resource that refers to the application configuration
     * file itself.
     */
    public Resource getConfigResource ()
    {
        try {
            return createResource(CONFIG_FILE);
        } catch (Exception e) {
            throw new RuntimeException("Invalid appbase '" + _vappbase + "'.");
        }
    }

    /**
     * Returns a list of the code {@link Resource} objects used by this
     * application.
     */
    public List getCodeResources ()
    {
        return _codes;
    }

    /**
     * Returns a list of the non-code {@link Resource} objects used by
     * this application.
     */
    public List getResources ()
    {
        return _resources;
    }

    /**
     * Returns a resource that can be used to download a patch file that
     * will bring this application from its current version to the target
     * version.
     */
    public Resource getPatchResource ()
    {
        if (_targetVersion <= _version) {
            Log.warning("Requested patch resource for up-to-date or " +
                        "non-versioned application [cvers=" + _version +
                        ", tvers=" + _targetVersion + "].");
            return null;
        }

        String pfile = "patch" + _version + ".dat";
        try {
            URL remote = new URL(createVAppBase(_targetVersion), pfile);
            return new Resource(pfile, remote, getLocalPath(pfile));
        } catch (Exception e) {
            Log.warning("Failed to create patch resource path [pfile=" + pfile +
                        ", appbase=" + _appbase + ", tvers=" + _targetVersion +
                        ", error=" + e + "].");
            return null;
        }
    }

    /**
     * Instructs the application to parse its <code>getdown.txt</code>
     * configuration and prepare itself for operation. The application
     * base URL will be parsed first so that if there are errors
     * discovered later, the caller can use the application base to
     * download a new <code>config.txt</code> file and try again.
     *
     * @return a configured UpdateInterface instance that will be used to
     * configure the update UI.
     *
     * @exception IOException thrown if there is an error reading the file
     * or an error encountered during its parsing.
     */
    public UpdateInterface init (boolean checkPlatform)
        throws IOException
    {
        // parse our configuration file
        HashMap cdata = null;
        try {
            cdata = ConfigUtil.parseConfig(_config, checkPlatform);
        } catch (FileNotFoundException fnfe) {
            // thanks to funny windows bullshit, we have to do this backup
            // file fiddling in case we got screwed while updating our
            // very critical getdown config file
            File cbackup = getLocalPath(CONFIG_FILE + "_old");
            if (cbackup.exists()) {
                cdata = ConfigUtil.parseConfig(cbackup, checkPlatform);
            } else {
                throw fnfe;
            }
        }

        // first determine our application base, this way if anything goes
        // wrong later in the process, our caller can use the appbase to
        // download a new configuration file
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
                throw new NestableIOException(err, e);
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
            throw new NestableIOException(err, mue);
        }

        // determine our application class name
        _class = (String)cdata.get("class");
        if (_class == null) {
            throw new IOException("m.missing_class");
        }

        // clear our arrays as we may be reinitializing
        _codes.clear();
        _resources.clear();
        _jvmargs.clear();
        _appargs.clear();

        // parse our code resources
        String[] codes = ConfigUtil.getMultiValue(cdata, "code");
        if (codes == null) {
            throw new IOException("m.missing_code");
        }
        for (int ii = 0; ii < codes.length; ii++) {
            try {
                _codes.add(createResource(codes[ii]));
            } catch (Exception e) {
                Log.warning("Invalid code resource '" + codes[ii] + "'." + e);
            }
        }

        // parse our non-code resources
        String[] rsrcs = ConfigUtil.getMultiValue(cdata, "resource");
        if (rsrcs != null) {
            for (int ii = 0; ii < rsrcs.length; ii++) {
                try {
                    _resources.add(createResource(rsrcs[ii]));
                } catch (Exception e) {
                    Log.warning("Invalid resource '" + rsrcs[ii] + "'. " + e);
                }
            }
        }

        // transfer our JVM arguments
        String[] jvmargs = ConfigUtil.getMultiValue(cdata, "jvmarg");
        if (jvmargs != null) {
            for (int ii = 0; ii < jvmargs.length; ii++) {
                _jvmargs.add(jvmargs[ii]);
            }
        }

        // transfer our application arguments
        String[] appargs = ConfigUtil.getMultiValue(cdata, "apparg");
        if (appargs != null) {
            for (int ii = 0; ii < appargs.length; ii++) {
                _appargs.add(appargs[ii]);
            }
        }

        // TODO: make this less of a hack
        String username = System.getProperty("username");
        if (!StringUtil.blank(username)) {
            _jvmargs.add("-Dusername=" + username);
        }
        String password = System.getProperty("password");
        if (!StringUtil.blank(password)) {
            _jvmargs.add("-Dpassword=" + password);
        }

        // look for custom arguments
        File file = getLocalPath("extra.txt");
        if (file.exists()) {
            try {
                List args = ConfigUtil.parsePairs(file, false);
                for (Iterator iter = args.iterator(); iter.hasNext(); ) {
                    String[] pair = (String[])iter.next();
                    _jvmargs.add(pair[0] + "=" + pair[1]);
                }
            } catch (Throwable t) {
                Log.warning("Failed to parse '" + file + "': " + t);
            }
        }

        // parse and return our application config
        UpdateInterface ui = new UpdateInterface();
        _name = ui.name = (String)cdata.get("ui.name");
        ui.progress = parseRect(cdata, "ui.progress", ui.progress);
        ui.progressText = parseColor(
            cdata, "ui.progress_text", ui.progressText);
        ui.progressBar = parseColor(
            cdata, "ui.progress_bar", ui.progressBar);
        ui.status = parseRect(cdata, "ui.status", ui.status);
        ui.statusText = parseColor(cdata, "ui.status_text", ui.statusText);
        ui.textShadow = parseColor(cdata, "ui.text_shadow", ui.textShadow);
        ui.background = (String)cdata.get("ui.background");
        return ui;
    }

    /**
     * Returns a URL from which the specified path can be fetched. Our
     * application base URL is properly versioned and combined with the
     * supplied path.
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
     * Attempts to redownload the <code>getdown.txt</code> file based on
     * information parsed from a previous call to {@link #init}.
     */
    public void attemptRecovery (StatusDisplay status)
        throws IOException
    {
        status.updateStatus("m.updating_metadata");
        downloadControlFile(CONFIG_FILE);
    }

    /**
     * Downloads and replaces the <code>getdown.txt</code> and
     * <code>digest.txt</code> files with those for the target version of
     * our application.
     */
    public void updateMetadata ()
        throws IOException
    {
        try {
            // update our versioned application base with the target version
            _vappbase = createVAppBase(_targetVersion);
        } catch (MalformedURLException mue) {
            String err = MessageUtil.tcompose("m.invalid_appbase", _appbase);
            throw new NestableIOException(err, mue);
        }

        // now re-download our control files; we download the digest first
        // so that if it fails, our config file will still reference the
        // old version and re-running the updater will start the whole
        // process over again
        downloadControlFile(Digest.DIGEST_FILE);
        downloadControlFile(CONFIG_FILE);
    }

    /**
     * Invokes the process associated with this application definition.
     */
    public Process createProcess ()
        throws IOException
    {
        // create our classpath
        StringBuffer cpbuf = new StringBuffer();
        for (Iterator iter = _codes.iterator(); iter.hasNext(); ) {
            if (cpbuf.length() > 0) {
                cpbuf.append(File.pathSeparator);
            }
            Resource rsrc = (Resource)iter.next();
            cpbuf.append(rsrc.getLocal().getAbsolutePath());
        }

        ArrayList args = new ArrayList();

        // reconstruct the path to the JVM
        args.add(LaunchUtil.getJVMPath());

        // add the classpath arguments
        args.add("-classpath");
        args.add(cpbuf.toString());

        // we love our Mac users, so we do nice things to preserve our
        // application identity
        if (RunAnywhere.isMacOS()) {
            args.add("-Xdock:icon=" + _appdir.getAbsolutePath() +
                "/../desktop.icns");
            args.add("-Xdock:name=" + _name);
        }

        // pass along our proxy settings
        String proxyHost;
        if ((proxyHost = System.getProperty("http.proxyHost")) != null) {
            args.add("-Dhttp.proxyHost=" + proxyHost);
            args.add("-Dhttp.proxyPort=" + System.getProperty("http.proxyPort"));
        }

        // pass along any pass-through arguments
        for (Iterator itr = System.getProperties().entrySet().iterator();
                itr.hasNext(); ) {
            Map.Entry entry = (Map.Entry) itr.next();
            String key = (String) entry.getKey();
            if (key.startsWith(PROP_PASSTHROUGH_PREFIX)) {
                key = key.substring(PROP_PASSTHROUGH_PREFIX.length());
                args.add("-D" + key + "=" + entry.getValue());
            }
        }

        // add the JVM arguments
        for (Iterator iter = _jvmargs.iterator(); iter.hasNext(); ) {
            args.add(processArg((String)iter.next()));
        }

        // add the application class name
        args.add(_class);

        // finally add the application arguments
        for (Iterator iter = _appargs.iterator(); iter.hasNext(); ) {
            args.add(processArg((String)iter.next()));
        }

        String[] sargs = new String[args.size()];
        args.toArray(sargs);

        Log.info("Running " + StringUtil.join(sargs, "\n  "));
        return Runtime.getRuntime().exec(sargs, null);
    }

    /** Replaces the application directory and version in any argument. */
    protected String processArg (String arg)
    {
        arg = StringUtil.replace(arg, "%APPDIR%", _appdir.getAbsolutePath());
        arg = StringUtil.replace(arg, "%VERSION%", String.valueOf(_version));
        return arg;
    }

    /**
     * Loads the <code>digest.txt</code> file and verifies the contents of
     * both that file and the <code>getdown.text</code> file. Then it
     * loads the <code>version.txt</code> and decides whether or not the
     * application needs to be updated or whether we can proceed to
     * verification and execution.
     *
     * @return true if the application needs to be updated, false if it is
     * up to date and can be verified and executed.
     *
     * @exception IOException thrown if we encounter an unrecoverable
     * error while verifying the metadata.
     */
    public boolean verifyMetadata (StatusDisplay status)
        throws IOException
    {
        Log.info("Verifying application: " + _vappbase);
        Log.info("Version: " + _version);
        Log.info("Class: " + _class);
//         Log.info("Code: " + StringUtil.toString(_codes.iterator()));
//         Log.info("Resources: " + StringUtil.toString(_resources.iterator()));
//         Log.info("JVM Args: " + StringUtil.toString(_jvmargs.iterator()));
//         Log.info("App Args: " + StringUtil.toString(_appargs.iterator()));

        // create our digester which will read in the contents of the
        // digest file and validate itself
        try {
            _digest = new Digest(_appdir);
        } catch (IOException ioe) {
            Log.info("Failed to load digest: " + ioe.getMessage() + ". " +
                     "Attempting recovery...");
        }

        // if we have no version, then we are running in unversioned mode
        // so we need to download our digest.txt file on every invocation
        if (_version == -1) {
            // make a note of the old meta-digest, if this changes we need
            // to revalidate all of our resources as one or more of them
            // have also changed
            String olddig = (_digest == null) ? "" : _digest.getMetaDigest();
            try {
                status.updateStatus("m.checking");
                downloadControlFile(Digest.DIGEST_FILE);
                _digest = new Digest(_appdir);
                if (!olddig.equals(_digest.getMetaDigest())) {
                    Log.info("Unversioned digest changed. Revalidating...");
                    status.updateStatus("m.validating");
                    clearValidationMarkers();
                }
            } catch (IOException ioe) {
                Log.warning("Failed to refresh non-versioned digest: " +
                            ioe.getMessage() + ". Proceeding...");
            }
        }

        // regardless of whether we're versioned, if we failed to read the
        // digest from disk, try to redownload the digest file and give it
        // another good college try; this time we allow exceptions to
        // propagate up to the caller as there is nothing else we can do
        if (_digest == null) {
            status.updateStatus("m.updating_metadata");
            downloadControlFile(Digest.DIGEST_FILE);
            _digest = new Digest(_appdir);
        }

        // now verify the contents of our main config file
        Resource crsrc = getConfigResource();
        if (!_digest.validateResource(crsrc, null)) {
            status.updateStatus("m.updating_metadata");
            // attempt to redownload both of our metadata files; again we
            // pass errors up to our caller because there's nothing we can
            // do to automatically recover
            downloadControlFile(CONFIG_FILE);
            downloadControlFile(Digest.DIGEST_FILE);
            _digest = new Digest(_appdir);
            // revalidate everything if we end up downloading new metadata
            clearValidationMarkers();
            // if the new copy validates, reinitialize ourselves;
            // otherwise report baffling hoseage
            if (_digest.validateResource(crsrc, null)) {
                init(true);
            } else {
                Log.warning(CONFIG_FILE + " failed to validate even after " +
                            "redownloading. Blindly forging onward.");
            }
        }

        // start by assuming we are happy with our version
        _targetVersion = _version;

        // if we are a versioned application, read in the contents of the
        // version.txt file
        if (_version != -1) {
            File vfile = getLocalPath(VERSION_FILE);
            FileInputStream fin = null;
            try {
                fin = new FileInputStream(vfile);
                BufferedReader bin = new BufferedReader(
                    new InputStreamReader(fin));
                String vstr = bin.readLine();
                if (!StringUtil.blank(vstr)) {
                    _targetVersion = Long.parseLong(vstr);
                }
            } catch (Exception e) {
                Log.info("Unable to read version file: " + e.getMessage());
            } finally {
                StreamUtil.close(fin);
            }
        }

        // finally let the caller know if we need an update
        return _version != _targetVersion;
    }

    /**
     * Verifies the code and media resources associated with this
     * application. A list of resources that do not exist or fail the
     * verification process will be returned. If all resources are ready
     * to go, null will be returned and the application is considered
     * ready to run.
     */
    public List verifyResources (ProgressObserver obs)
    {
        ArrayList rsrcs = new ArrayList(), failures = new ArrayList();
        rsrcs.addAll(_codes);
        rsrcs.addAll(_resources);

        // total up the file size of the resources to validate
        long totalSize = 0L;
        for (Iterator iter = rsrcs.iterator(); iter.hasNext(); ) {
            Resource rsrc = (Resource)iter.next();
            totalSize += rsrc.getLocal().length();
        }

        MetaProgressObserver mpobs = new MetaProgressObserver(obs, totalSize);
        for (Iterator iter = rsrcs.iterator(); iter.hasNext(); ) {
            Resource rsrc = (Resource)iter.next();
            mpobs.startElement(rsrc.getLocal().length());

            if (rsrc.isMarkedValid()) {
                mpobs.progress(100);
                continue;
            }

            try {
                if (_digest.validateResource(rsrc, mpobs)) {
                    // make a note that this file is kosher
                    rsrc.markAsValid();
                    continue;
                }

            } catch (Exception e) {
                Log.info("Failure validating resource [rsrc=" + rsrc +
                         ", error=" + e + "]. Requesting redownload...");

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
        clearValidationMarkers(_codes.iterator());
        clearValidationMarkers(_resources.iterator());
    }

    /**
     * Creates a versioned application base URL for the specified version.
     */
    protected URL createVAppBase (long version)
        throws MalformedURLException
    {
        return new URL(
            StringUtil.replace(_appbase, "%VERSION%", "" + version));
    }

    /** Clears all validation marker files for the resources in the
     * supplied iterator. */
    protected void clearValidationMarkers (Iterator iter)
    {
        while (iter.hasNext()) {
            ((Resource)iter.next()).clearMarker();
        }
    }

    /**
     * Downloads a new copy of the specified control file and, if the
     * download is successful, moves it over the old file on the
     * filesystem.
     */
    protected void downloadControlFile (String path)
        throws IOException
    {
        File target = getLocalPath(path + "_new");
        URL targetURL = null;
        try {
            targetURL = getRemoteURL(path);
        } catch (Exception e) {
            Log.warning("Requested to download invalid control file " +
                        "[appbase=" + _vappbase + ", path=" + path +
                        ", error=" + e + "].");
            throw new NestableIOException("Invalid path '" + path + "'.", e);
        }

        Log.info("Attempting to refetch '" + path + "' from '" +
                 targetURL + "'.");

        // stream the URL into our temporary file
        InputStream fin = null;
        FileOutputStream fout = null;
        try {
            fin = targetURL.openStream();
            fout = new FileOutputStream(target);
            CopyUtils.copy(fin, fout);
        } finally {
            StreamUtil.close(fin);
            StreamUtil.close(fout);
        }

        // Windows is a wonderful operating system, it won't let you
        // rename a file overtop of another one; thus to avoid running the
        // risk of getting royally fucked, we have to do this complicated
        // backup bullshit; this way if the shit hits the fan before we
        // get the new copy into place, we should be able to read from the
        // backup copy; yay!
        File original = getLocalPath(path);
        if (RunAnywhere.isWindows() && original.exists()) {
            File backup = getLocalPath(path + "_old");
            if (backup.exists() && !backup.delete()) {
                Log.warning("Failed to delete " + backup + ".");
            }
            if (!original.renameTo(backup)) {
                Log.warning("Failed to move " + original + " to backup. " +
                            "We will likely fail to replace it with " +
                            target + ".");
            }
        }

        // now attempt to replace the current file with the new one
        if (!target.renameTo(original)) {
            throw new IOException(
                "Failed to rename(" + target + ", " + original + ")");
        }
    }

    /** Helper function for creating {@link Resource} instances. */
    protected Resource createResource (String path)
        throws MalformedURLException
    {
        return new Resource(path, getRemoteURL(path), getLocalPath(path));
    }

    /** Used to parse rectangle specifications from the config file. */
    protected Rectangle parseRect (HashMap cdata, String name, Rectangle def)
    {
        String value = (String)cdata.get(name);
        if (!StringUtil.blank(value)) {
            int[] v = StringUtil.parseIntArray(value);
            if (v != null && v.length == 4) {
                return new Rectangle(v[0], v[1], v[2], v[3]);
            } else {
                Log.warning("Ignoring invalid '" + name + "' config '" +
                            value + "'.");
            }
        }
        return def;
    }

    /** Used to parse color specifications from the config file. */
    protected Color parseColor (HashMap cdata, String name, Color def)
    {
        String value = (String)cdata.get(name);
        if (!StringUtil.blank(value)) {
            try {
                return new Color(Integer.parseInt(value, 16));
            } catch (Exception e) {
                Log.warning("Ignoring invalid '" + name + "' config '" +
                            value + "'.");
            }
        }
        return def;
    }

    protected File _appdir;
    protected File _config;
    protected Digest _digest;

    protected long _version = -1;
    protected long _targetVersion = -1;
    protected String _appbase;
    protected URL _vappbase;
    protected String _class;
    protected String _name;

    protected ArrayList _codes = new ArrayList();
    protected ArrayList _resources = new ArrayList();

    protected ArrayList _jvmargs = new ArrayList();
    protected ArrayList _appargs = new ArrayList();
}
