//
// $Id: Application.java,v 1.1 2004/07/02 11:01:21 mdb Exp $

package com.threerings.getdown.data;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.samskivert.io.NestableIOException;
import com.samskivert.text.MessageUtil;
import com.samskivert.util.StringUtil;

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

    /**
     * Creates an application instance which records the location of the
     * <code>getdown.txt</code> configuration file from the supplied
     * application directory.
     */
    public Application (File appdir)
    {
        _appdir = appdir;
        _config = new File(appdir, CONFIG_FILE);
    }

    /**
     * Returns a resource that refers to the application configuration
     * file itself.
     */
    public Resource getConfigResource ()
    {
        try {
            return new Resource(_appbase, _appdir, CONFIG_FILE);
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

        // determine our application class name
        _class = (String)cdata.get("class");
        if (_class == null) {
            throw new IOException("m.missing_class");
        }

        // parse our code resources
        String[] codes = ConfigUtil.getMultiValue(cdata, "code");
        if (codes == null) {
            throw new IOException("m.missing_code");
        }
        for (int ii = 0; ii < codes.length; ii++) {
            try {
                _codes.add(new Resource(_appbase, _appdir, codes[ii]));
            } catch (Exception e) {
                Log.warning("Invalid code resource '" + codes[ii] + "'.");
            }
        }

        // parse our non-code resources
        String[] rsrcs = ConfigUtil.getMultiValue(cdata, "resource");
        if (rsrcs != null) {
            for (int ii = 0; ii < rsrcs.length; ii++) {
                try {
                    _resources.add(new Resource(_appbase, _appdir, rsrcs[ii]));
                } catch (Exception e) {
                    Log.warning("Invalid resource '" + rsrcs[ii] + "'.");
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

    protected File _appdir;
    protected File _config;

    protected int _version = -1;
    protected URL _appbase;
    protected String _class;

    protected ArrayList _codes = new ArrayList();
    protected ArrayList _resources = new ArrayList();

    protected ArrayList _jvmargs = new ArrayList();
    protected ArrayList _appargs = new ArrayList();
}
