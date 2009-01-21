//
// $Id$
//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2009 Three Rings Design, Inc.
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

package com.threerings.getdown.launcher;

import java.awt.Color;
import java.awt.Rectangle;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.Properties;

import javax.swing.JApplet;

import com.samskivert.util.RunAnywhere;
import com.samskivert.util.StringUtil;
import com.threerings.getdown.data.Application;
import com.threerings.getdown.launcher.ImageLoader;
import com.threerings.getdown.launcher.RotatingBackgrounds;
import com.threerings.getdown.util.ConfigUtil;

import static com.threerings.getdown.Log.log;

public class GetdownAppletConfig
{
    public static final String APPBASE = "appbase";

    public static final String APPNAME = "appname";

    public static final String BGIMAGE = "bgimage";

    public static final String ERRORBGIMAGE = "errorbgimage";

    public static final String PROPERTIES = "properties";

    public static final String PARAM_DELIMITER = ".";

    public static final String APP_PROPERTIES = "app_properties";

    public static final String DIRECT = "direct";

    public static final String REDIRECT_ON_FINISH = "redirect_on_finish";

    public static final String REDIRECT_ON_FINISH_TARGET = "redirect_on_finish_target";

    public String appbase;

    public String appname;

    /** The list of background images and their display time */
    public String imgpath;

    /** The error background image */
    public String errorimgpath;

    public Properties properties = new Properties();

    public File appdir;

    public String[] jvmargs;

    /** An optional URL to which the applet should redirect when done. */
    public URL redirectUrl;

    public String redirectTarget;

    public String installerFileContents;

    /**
     * Indicates whether the downloaded app should be launched in the parent applet (true) or as a
     * separate java process (false).
     */
    public boolean invokeDirect;

    /** Optional default bounds for the status panel. */
    public Rectangle statusBounds;

    public Color statusColor;

    public GetdownAppletConfig (JApplet applet)
    {
        this(applet, null);
    }

    public GetdownAppletConfig (JApplet applet, String paramPrefix)
    {
        _applet = applet;
        _prefix = paramPrefix;

        appbase = getParameter(APPBASE, "");
        appname = getParameter(APPNAME, "");
        log.info("App Base: " + appbase);
        log.info("App Name: " + appname);

        imgpath = getParameter(BGIMAGE);
        errorimgpath = getParameter(ERRORBGIMAGE);

        String props = getParameter(PROPERTIES);
        if (props != null) {
            String[] proparray = props.split(" ");
            for (String property : proparray) {
                String key = property.substring(property.indexOf("-D") + 2, property.indexOf("="));
                String value = property.substring(property.indexOf("=") + 1);
                properties.setProperty(key, value);
            }
        }

        String root;
        if (RunAnywhere.isWindows()) {
            root = "Application Data";
            String verStr = System.getProperty("os.version");
            try {
                if (Float.parseFloat(verStr) >= 6.0f) {
                    // Vista makes us write it here.... Yay.
                    root = "AppData" + File.separator + "LocalLow";
                }
            } catch (Exception e) {
                log.warning("Couldn't parse OS version", "vers", verStr, "error", e);
            }
        } else if (RunAnywhere.isMacOS()) {
            root = "Library" + File.separator + "Application Support";
        } else /* isLinux() or something wacky */{
            root = ".getdown";
        }
        appdir = new File(System.getProperty("user.home") + File.separator + root + File.separator
            + appname);

        installerFileContents = getParameter("installer");

        // Pull out -D properties to pass through to the launched vm if they exist
        String params = getParameter(APP_PROPERTIES);
        if (params == null) {
            jvmargs = new String[0];
        } else {
            jvmargs = params.split(",");
            for (int ii = 0; ii < jvmargs.length; ii++) {
                jvmargs[ii] = "-D" + jvmargs[ii];
            }
        }

        String direct = getParameter(DIRECT, "false");
        invokeDirect = Boolean.parseBoolean(direct);

        String redirectURL = getParameter(REDIRECT_ON_FINISH);
        if (redirectURL != null) {
            try {
                redirectUrl = new URL(redirectURL);
                redirectTarget = getParameter(REDIRECT_ON_FINISH_TARGET);
            } catch (MalformedURLException e) {
                log.warning("URL in " + REDIRECT_ON_FINISH + " param is malformed: " + e);
            }
        }

        // This allows us to configure the status panel from applet parameters in case something
        // goes horribly wrong before we get a chance to read getdown.txt (like when the user
        // rejects write permission for the applet)
        statusBounds = Application.parseRect(getParameter("ui.status"));
        statusColor = Application.parseColor(getParameter("ui.status_text"));
    }

    /**
     * Does all the fiddly initialization of Getdown and throws an exception if something goes
     * horribly wrong.
     */
    public void init () throws Exception
    {
        securityCheck();
        setSystemProperties();
        ensureAppdirExists();
        createFiles();

        // record a few things for posterity
        log.info("------------------ VM Info ------------------");
        log.info("-- OS Name: " + System.getProperty("os.name"));
        log.info("-- OS Arch: " + System.getProperty("os.arch"));
        log.info("-- OS Vers: " + System.getProperty("os.version"));
        log.info("-- Java Vers: " + System.getProperty("java.version"));
        log.info("-- Java Home: " + System.getProperty("java.home"));
        log.info("-- User Name: " + System.getProperty("user.name"));
        log.info("-- User Home: " + System.getProperty("user.home"));
        log.info("-- Cur dir: " + System.getProperty("user.dir"));
        log.info("---------------------------------------------");
    }

    /**
     * Sets getdown status panel size and text color from applet params.
     */
    public void config (Getdown getdown)
    {
        if (statusBounds != null) {
            getdown._ifc.status = statusBounds;
        }
        if (statusColor != null) {
            getdown._ifc.statusText = statusColor;
        }
    }

    /**
     * Gets the value of the named parameter. If the param is not set, this will return null.
     */
    public String getParameter (String param)
    {
        if (_prefix != null) {
            return _applet.getParameter(_prefix + PARAM_DELIMITER + param);
        }
        return _applet.getParameter(param);
    }

    /**
     * Gets the value of an optional Applet parameter. If the param is not set, the default value is
     * returned.
     */
    public String getParameter (String param, String defaultValue)
    {
        String value = getParameter(param);
        return value == null ? defaultValue : value;
    }

    /**
     * Uses the Applet context to redirect the browser to a URL (intended for use when the applet
     * is done downloading). If the redirect_on_finish parameter was not set, this does nothing.
     */
    public void redirect ()
    {
        if (redirectUrl != null) {
            if (redirectTarget == null) {
                _applet.getAppletContext().showDocument(redirectUrl);
            } else {
                _applet.getAppletContext().showDocument(redirectUrl, redirectTarget);
            }
        }
    }

    /**
     *
     */
    public RotatingBackgrounds getBackgroundImages (ImageLoader loader)
    {
        if (bgimages == null) {
            // Load background images
            bgimages = getBackgroundImages(imgpath, errorimgpath, loader);
        }
        return bgimages;
    }

    public static RotatingBackgrounds getBackgroundImages (String imageParam,
        String errorImagePath, ImageLoader loader)
    {
        // Parse the image parameter and load the background images
        RotatingBackgrounds images;
        if (imageParam == null) {
            images = new RotatingBackgrounds();
        } else if (imageParam.indexOf(",") > -1) {
            images = new RotatingBackgrounds(imageParam.split(","), errorImagePath, loader);
        } else {
            images = new RotatingBackgrounds(loader.loadImage(imageParam));
        }
        return images;
    }

    /**
     * This checks whether the user has accepted our signed
     */
    protected void securityCheck () throws Exception
    {
        // getdown requires full read/write permissions to the system; if we don't have this, then
        // we need to not do anything unsafe, and display a message to the user telling them they
        // need to (groan) close out of the web browser entirely and re-launch the browser, go to
        // our site, and accept the certificate
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            try {
                sm.checkPropertiesAccess();
                sm.checkWrite("getdown");
            } catch (SecurityException se) {
                log.warning("Signed applet rejected by user", "se", se);
                throw new Exception("m.insufficient_permissions_error");
            }
        }
    }

    /**
     * Dumps the contents of the <code>properties</code> into the System properties.
     */
    protected void setSystemProperties ()
    {
        System.getProperties().putAll(properties);
    }

    /**
     * Makes sire that the appdir directory exists, creating it if necessary. Throws an exception if
     * the directory does not exist and cannot be created.
     */
    protected void ensureAppdirExists () throws Exception
    {
        // if our application directory does not exist, auto-create it
        if (!appdir.exists() || !appdir.isDirectory()) {
            if (!appdir.mkdirs()) {
                throw new Exception("m.create_appdir_failed");
            }
        }
    }

    /**
     * Writes the installer.txt and getdown.txt files to the local file system as needed.
     */
    protected void createFiles () throws Exception
    {
        // if an installer.txt file is desired, create that
        if (!StringUtil.isBlank(installerFileContents)) {
            File infile = new File(appdir, "installer.txt");
            if (!infile.exists()) {
                writeToFile(infile, installerFileContents);
            }
        }

        // if our getdown.txt file does not exist, or it is corrupt, auto-/recreate it
        File gdfile = new File(appdir, "getdown.txt");
        boolean createGetdown = !gdfile.exists();
        if (!createGetdown) {
            try {
                Map<String,Object> cdata = ConfigUtil.parseConfig(gdfile, false);
                String oappbase = StringUtil.trim((String)cdata.get(APPBASE));
                createGetdown = (appbase != null && !appbase.trim().equals(oappbase));
                if (createGetdown) {
                    log.warning("Recreating getdown.txt due to appbase mismatch",
                                "nappbase", appbase, "oappbase", oappbase);
                }
            } catch (Exception e) {
                log.warning("Failure checking validity of getdown.txt, forcing recreate.",
                            "error", e);
                createGetdown = true;
            }
        }
        if (createGetdown) {
            if (StringUtil.isBlank(appbase)) {
                throw new Exception("m.missing_appbase");
            }
            if (!writeToFile(gdfile, "appbase = " + appbase)) {
                throw new Exception("m.create_getdown_failed");
            }
        }
    }

    /**
     * Creates the specified file and writes the supplied contents to it.
     */
    protected boolean writeToFile (File tofile, String contents)
    {
        try {
            PrintStream out = new PrintStream(new FileOutputStream(tofile));
            out.println(contents);
            out.close();
            return true;
        } catch (IOException ioe) {
            log.warning("Failed to create '" + tofile + "'.", ioe);
            return false;
        }
    }

    /** A reference to the Applet in which Getdown is running */
    protected JApplet _applet;

    /** An optional prefix to prepend when looking for Getdown Applet params */
    protected String _prefix;

    /** The background images displayed on the status panel as Getdown is getting down. */
    protected RotatingBackgrounds bgimages;
}
