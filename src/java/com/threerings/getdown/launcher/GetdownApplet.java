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

import java.awt.Container;
import java.awt.Image;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.MalformedURLException;
import java.net.URL;

import javax.swing.JApplet;
import javax.swing.JPanel;

import com.samskivert.util.RunAnywhere;
import com.samskivert.util.StringUtil;
import com.threerings.getdown.Log;

/**
 * An applet that can be used to launch a Getdown application (when signed and
 * given privileges).
 */
public class GetdownApplet extends JApplet
    implements ImageLoader
{
    @Override // documentation inherited
    public void init ()
    {
        // verify that we are not being hijacked to execute malicious code in the name of the
        // signer
        String appbase = getParameter("appbase");
        String appname = getParameter("appname");
        String imgpath = getParameter("bgimage");;
        if (appbase == null) {
            appbase = "";
        }
        if (appname == null) {
            appname = "";
        }

        final RotatingBackgrounds bgimages;
        if (imgpath == null) {
            bgimages = new RotatingBackgrounds();
        } else if (imgpath.contains(",")) {
            bgimages = new RotatingBackgrounds(imgpath.split(","), this);
        } else {
            bgimages = new RotatingBackgrounds(loadImage(imgpath));
        }

        Log.info("App Base: " + appbase);
        Log.info("App Name: " + appname);

        File appdir = null;
        try {
            appdir = initGetdown(appbase, appname, imgpath);

            // record a few things for posterity
            Log.info("------------------ VM Info ------------------");
            Log.info("-- OS Name: " + System.getProperty("os.name"));
            Log.info("-- OS Arch: " + System.getProperty("os.arch"));
            Log.info("-- OS Vers: " + System.getProperty("os.version"));
            Log.info("-- Java Vers: " + System.getProperty("java.version"));
            Log.info("-- Java Home: " + System.getProperty("java.home"));
            Log.info("-- User Name: " + System.getProperty("user.name"));
            Log.info("-- User Home: " + System.getProperty("user.home"));
            Log.info("-- Cur dir: " + System.getProperty("user.dir"));
            Log.info("---------------------------------------------");

        } catch (Exception e) {
            _errmsg = e.getMessage();
        }

        try {
            // XXX getSigners() returns all certificates used to sign this applet which may allow
            // a third party to insert a trusted certificate. This should be replaced with
            // statically included trusted keys.
            _getdown = new Getdown(appdir, null, GetdownApplet.class.getSigners()) {
                protected Container createContainer () {
                    getContentPane().removeAll();
                    return getContentPane();
                }
                protected RotatingBackgrounds getBackground () {
                    return bgimages;
                }
                protected void showContainer () {
                    ((JPanel)getContentPane()).revalidate();
                }
                protected void disposeContainer () {
                    // nothing to do as we're in an applet
                }
                protected boolean invokeDirect () {
                    return "true".equalsIgnoreCase(getParameter("direct"));
                }
                protected JApplet getApplet () {
                    return GetdownApplet.this;
                }
                protected void exit (int exitCode) {
                    // don't exit as we're in an applet
                }
            };

            // set up our user interface immediately
            _getdown.preInit();

        } catch (Exception e) {
            Log.logStackTrace(e);
        }
    }

    public Image loadImage (String path)
    {
        try {
            return getImage(new URL(getDocumentBase(), path));
        } catch (MalformedURLException e) {
            Log.warning("Failed to load background image [path=" + path + "].");
            Log.logStackTrace(e);
            return null;
        }
    }

    @Override // documentation inherited
    public void start ()
    {
        if (_errmsg != null) {
            _getdown.updateStatus(_errmsg);
        } else {
            try {
                _getdown.start();
            } catch (Exception e) {
                Log.logStackTrace(e);
            }
        }
    }

    @Override // documentation inherited
    public void stop ()
    {
        // TODO
    }

    /**
     * Does all the fiddly initialization of Getdown and throws an exception if something goes
     * horribly wrong. If an exception is thrown we will abort the whole process and display an
     * error message to the user.
     */
    protected File initGetdown (String appbase, String appname, String imgpath)
        throws Exception
    {
        // getdown requires full read/write permissions to the system; if we don't have this, then
        // we need to not do anything unsafe, and display a message to the user telling them they
        // need to (groan) close out of the web browser entirely and re-launch the browser, go to
        // our site, and accept the certificate
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            try {
                sm.checkWrite("getdown");
                sm.checkPropertiesAccess();
            } catch (SecurityException se) {
                Log.warning("Signed applet rejected by user [se=" + se + "].");
                throw new Exception("m.insufficient_permissions_error");
            }
        }

        // pass through properties parameters
        String properties = getParameter("properties");
        if (properties != null) {
            String[] proparray = properties.split(" ");
            for (String property : proparray) {
                String key = property.substring(property.indexOf("-D") + 2, property.indexOf("="));
                String value = property.substring(property.indexOf("=") + 1);
                System.setProperty(key, value);
            }
        }

        // when run from an applet, we install to the user's home directory
        String root;
        if (RunAnywhere.isWindows()) {
            root = "Application Data";
            String verStr = System.getProperty("os.version");
            try {
                if (Float.parseFloat(verStr) >= 6.0f) {
                    // Vista makes us write it here....  Yay.
                    root = "AppData" + File.separator + "LocalLow";
                }
            } catch (Exception e) {
                Log.warning("Couldn't parse OS version [vers=" + verStr + ", error=" + e + "].");
            }
        } else if (RunAnywhere.isMacOS()) {
            root = "Library" + File.separator + "Application Support";
        } else /* isLinux() or something wacky */ {
            root = ".getdown";
        }
        File appdir = new File(System.getProperty("user.home") + File.separator + root +
                               File.separator + appname);

        // if our application directory does not exist, auto-create it
        if (!appdir.exists() || !appdir.isDirectory()) {
            if (!appdir.mkdirs()) {
                throw new Exception("m.create_appdir_failed");
            }
        }

        // if an installer.txt file is desired, create that
        String inststr = getParameter("installer");
        if (!StringUtil.isBlank(inststr)) {
            File infile = new File(appdir, "installer.txt");
            if (!infile.exists()) {
                writeToFile(infile, inststr);
            }
        }

        // if our getdown.txt file does not exist, auto-create it
        File gdfile = new File(appdir, "getdown.txt");
        if (!gdfile.exists()) {
            if (StringUtil.isBlank(appbase)) {
                throw new Exception("m.missing_appbase");
            }
            if (!writeToFile(gdfile, "appbase = " + appbase)) {
                throw new Exception("m.create_getdown_failed");
            }
        }

        return appdir;
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
            Log.warning("Failed to create '" + tofile + "'.");
            Log.logStackTrace(ioe);
            return false;
        }
    }

    /** Handles all the actual getting down. */
    protected Getdown _getdown;

    /** An error encountered during initialization. */
    protected String _errmsg;
}
