//
// $Id: Application.java,v 1.2 2004/07/02 15:22:49 mdb Exp $

package com.threerings.getdown.data;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.samskivert.io.NestableIOException;
import com.samskivert.io.StreamUtil;
import com.samskivert.text.MessageUtil;
import com.samskivert.util.StringUtil;

import org.apache.commons.io.StreamUtils;

import com.threerings.getdown.Log;
import com.threerings.getdown.util.ConfigUtil;

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
            throw new RuntimeException("Booched appbase '" + _appbase + "'!?");
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
     * Instructs the application to parse its <code>getdown.txt</code>
     * configuration and prepare itself for operation. The application
     * base URL will be parsed first so that if there are errors
     * discovered later, the caller can use the application base to
     * download a new <code>config.txt</code> file and try again.
     *
     * @exception IOException thrown if there is an error reading the file
     * or an error encountered during its parsing.
     */
    public void init ()
        throws IOException
    {
        // parse our configuration file
        HashMap cdata = ConfigUtil.parseConfig(_config);

        // first determine our application base, this way if anything goes
        // wrong later in the process, our caller can use the appbase to
        // download a new configuration file
        String appbase = (String)cdata.get("appbase");
        if (appbase == null) {
            throw new IOException("m.missing_appbase");
        }
        try {
            // make sure there's a trailing slash
            if (!appbase.endsWith("/")) {
                appbase = appbase + "/";
            }
            _appbase = new URL(appbase);
        } catch (Exception e) {
            String err = MessageUtil.tcompose("m.invalid_appbase", appbase);
            throw new NestableIOException(err, e);
        }

        // extract our version information
        String vstr = (String)cdata.get("version");
        if (vstr != null) {
            try {
                _version = Integer.parseInt(vstr);
            } catch (Exception e) {
                String err = MessageUtil.tcompose("m.invalid_version", vstr);
                throw new NestableIOException(err, e);
            }
        }

        // if we are a versioned deployment, create a versioned appbase
        if (_version < 0) {
            _vappbase = _appbase;
        } else {
            try {
                _vappbase = new URL(
                    StringUtil.replace(_appbase.toString(), "%VERSION%", vstr));
            } catch (MalformedURLException mue) {
                String err = MessageUtil.tcompose("m.invalid_appbase", appbase);
                throw new NestableIOException(err, mue);
            }
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

//         Log.info("Parsed application " + _appbase);
//         Log.info("Version: " + _version);
//         Log.info("Class: " + _class);
//         Log.info("Code: " + StringUtil.toString(_codes.iterator()));
//         Log.info("Resources: " + StringUtil.toString(_resources.iterator()));
//         Log.info("JVM Args: " + StringUtil.toString(_jvmargs.iterator()));
//         Log.info("App Args: " + StringUtil.toString(_appargs.iterator()));
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
    public boolean verifyMetadata ()
        throws IOException
    {
        // create our digester which will read in the contents of the
        // digest file and validate itself
        try {
            _digest = new Digest(_appdir);
        } catch (IOException ioe) {
            Log.info("Failed to load digest: " + ioe.getMessage() + ". " +
                     "Attempting recovery...");
        }

        // if we failed to load the digest, try to redownload the digest
        // file and give it another good college try; this time we allow
        // exceptions to propagate up to the caller as there is nothing
        // else we can do to recover
        if (_digest == null) {
            downloadControlFile(Digest.DIGEST_FILE);
            _digest = new Digest(_appdir);
        }

        // now verify the contents of our main config file
        Resource crsrc = getConfigResource();
        if (!_digest.validateResource(crsrc)) {
            // attempt to redownload the file; again we pass errors up to
            // our caller because we have no recourse to recovery
            downloadControlFile(CONFIG_FILE);
            // if the new copy validates, reinitialize ourselves;
            // otherwise report baffling hoseage
            if (_digest.validateResource(crsrc)) {
                init();
            }
        }

        // start by assuming we are happy with our version
        _targetVersion = _version;

        // now read in the contents of the version.txt file (if any)
        File vfile = getLocalPath(VERSION_FILE);
        FileInputStream fin = null;
        try {
            fin = new FileInputStream(vfile);
            BufferedReader bin = new BufferedReader(
                new InputStreamReader(fin));
            String vstr = bin.readLine();
            if (!StringUtil.blank(vstr)) {
                _targetVersion = Integer.parseInt(vstr);
            }
        } catch (Exception e) {
            Log.info("Unable to read version file: " + e.getMessage());
        } finally {
            StreamUtil.close(fin);
        }

        // finally let the caller know if we need an update
        return _version != _targetVersion;
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
                        "[appbase=" + _appbase + ", path=" + path +
                        ", error=" + e + "].");
            throw new NestableIOException("Invalid path '" + path + "'.", e);
        }

        Log.info("Attempting to refetch '" + path + "'.");

        // stream the URL into our temporary file
        InputStream fin = null;
        FileOutputStream fout = null;
        try {
            fin = targetURL.openStream();
            fout = new FileOutputStream(target);
            StreamUtils.pipe(fin, fout);
        } finally {
            StreamUtil.close(fin);
            StreamUtil.close(fout);
        }

        // now attempt to replace the current file with the new one
        File original = getLocalPath(path);
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

    protected File _appdir;
    protected File _config;
    protected Digest _digest;

    protected int _version = -1;
    protected int _targetVersion = -1;
    protected URL _appbase, _vappbase;
    protected String _class;

    protected ArrayList _codes = new ArrayList();
    protected ArrayList _resources = new ArrayList();

    protected ArrayList _jvmargs = new ArrayList();
    protected ArrayList _appargs = new ArrayList();
}
