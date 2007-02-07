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
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.security.cert.Certificate;

import javax.swing.JApplet;
import javax.swing.JPanel;

import java.io.IOException;
import java.io.FileOutputStream;
import java.io.File;
import java.io.PrintStream;

import netscape.javascript.JSObject;
import netscape.javascript.JSException;

import org.apache.commons.codec.binary.Base64;

import com.samskivert.util.RunAnywhere;
import com.samskivert.util.StringUtil;

import com.threerings.getdown.Log;

/**
 * An applet that can be used to launch a Getdown application (when signed and
 * given privileges).
 */
public class GetdownApplet extends JApplet
{
    @Override // documentation inherited
    public void init ()
    {
        // verify that we are not being hijacked to execute malicious code in the name of the
        // signer
        String appbase = getParameter("appbase");
        String appname = getParameter("appname");
        String imgpath = getParameter("bgimage");
        if (appbase == null) {
            appbase = "";
        }
        if (appname == null) {
            appname = "";
        }
        if (imgpath == null) {
            imgpath = "";
        }

        Log.info("App Base: " + appbase);
        Log.info("App Name: " + appname);

        File appdir = null;
        try {
            appdir = initGetdown(appbase, appname, imgpath);

            // if a background image was specified, grabbit
            try {
                if (!StringUtil.isBlank(imgpath)) {
                    _bgimage = getImage(new URL(getDocumentBase(), imgpath));
                }
            } catch (Exception e) {
                Log.info("Failed to load background image [path=" + imgpath + "].");
                Log.logStackTrace(e);
            }

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
            _getdown = new Getdown(appdir, null) {
                protected Container createContainer () {
                    getContentPane().removeAll();
                    return getContentPane();
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
                protected Image getBackgroundImage () {
                    return _bgimage == null ?
                        super.getBackgroundImage() : _bgimage;
                }
                protected void exit (int exitCode) {
                    // don't exit as we're in an applet
                }
                @Override protected void setStatus (
                    final String message, final int percent, final long remaining, boolean createUI)
                {
                    super.setStatus(message, percent, remaining, createUI);
                    try {
                        JSObject.getWindow(GetdownApplet.this).call(
                            "getdownStatus", new Object[] { message, percent, remaining });
                    } catch (Throwable t) {
                        Log.warning("Failed to communicate status to JavaScript: " + t);
                    }
                }
            };

            // set up our user interface immediately
            _getdown.preInit();

        } catch (Exception e) {
            Log.logStackTrace(e);
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


        Object[] signers = GetdownApplet.class.getSigners();
        if (signers.length == 0) {
            Log.info("No signers, not verifying param signature.");

        } else {
            String signature = getParameter("signature");
            if (signature == null) {
                signature = "";
            }

            Log.info("Verifying signature '" + signature + "'.");
            String params = appbase + appname + imgpath;
            int validated = 0;
            for (Object signer : signers) {
                if (signer instanceof Certificate) {
                    Certificate cert = (Certificate)signer;
                    try {
                        Signature sig = Signature.getInstance("SHA1withRSA");
                        sig.initVerify(cert);
                        sig.update(params.getBytes());
                        if (!sig.verify(Base64.decodeBase64(signature.getBytes()))) {
                            Log.info("Signature does not match '" + cert.getPublicKey() + "'.");
                        }
                        validated++;
                    } catch (GeneralSecurityException gse) {
                        // no problem!
                    }
                }
            }
            // if we couldn't find a key that validates our parameters, we are the hosed!
            if (validated == 0) {
                throw new Exception("m.corrupt_param_signature_error");
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

    /** A background image drawn to make things look purdy. */
    protected Image _bgimage;

    /** An error encountered during initialization. */
    protected String _errmsg;
}
