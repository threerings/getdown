//
// $Id$
//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2008 Three Rings Design, Inc.
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

import java.io.IOException;
import java.io.FileOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.Map;

import com.samskivert.util.RunAnywhere;
import com.samskivert.util.StringUtil;

import com.threerings.getdown.util.ConfigUtil;

import static com.threerings.getdown.Log.log;

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
        String imgpath = getParameter("bgimage");
        String errorimgpath = getParameter("errorbgimage");
        if (appbase == null) {
            appbase = "";
        }
        if (appname == null) {
            appname = "";
        }

        final RotatingBackgrounds bgimages;
        if (imgpath == null) {
            bgimages = new RotatingBackgrounds();
        } else if (imgpath.indexOf(",") > -1) {
            bgimages = new RotatingBackgrounds(imgpath.split(","), errorimgpath, this);
        } else {
            bgimages = new RotatingBackgrounds(loadImage(imgpath));
        }

        log.info("App Base: " + appbase);
        log.info("App Name: " + appname);

        File appdir = null;
        try {
            appdir = initGetdown(appbase, appname, imgpath);

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

        } catch (Exception e) {
            _errmsg = e.getMessage();
        }

        // Pull out system properties to pass through to the launched vm if they exist
        String params = getParameter("app_properties");
        String[] jvmargs;
        if(params == null){
            jvmargs = new String[0];
        } else {
            jvmargs = params.split(",");
            for (int ii = 0; ii < jvmargs.length; ii++) {
                jvmargs[ii] = "-D" + jvmargs[ii];
            }    
        }

        try {
            // XXX getSigners() returns all certificates used to sign this applet which may allow
            // a third party to insert a trusted certificate. This should be replaced with
            // statically included trusted keys.
            _getdown = new Getdown(appdir, null, GetdownApplet.class.getSigners(), jvmargs) {
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
                    _app.releaseLock();
                    // Redirect to the URL in 'redirect_on_finish' if we completed successfully.
                    // This allows us to use some javascript on that page to close Getdown's 
                    // browser window.  I'd prefer to use the javascript bridge from the applet
                    // rather than redirecting, but calling JSObject.getWindow(this).call("close") 
                    // doesn't seem to do anything.
                    if (getParameter("redirect_on_finish") != null && exitCode == 0) {
                        URL dest;
                        try {
                            dest = new URL(getParameter("redirect_on_finish"));
                        } catch (MalformedURLException e) {
                            log.warning("URL in redirect_on_finish param is malformed: " + e);
                            return;
                        }
                        String target = getParameter("redirect_on_finish_target");
                        if (target == null) {
                            getAppletContext().showDocument(dest);
                        } else {
                            getAppletContext().showDocument(dest, target);
                        }
                    }
                }
            };

            // set up our user interface immediately
            _getdown.preInit();

        } catch (Exception e) {
            log.warning("init() failed.", e);
        }
    }

    public Image loadImage (String path)
    {
        try {
            return getImage(new URL(getDocumentBase(), path));
        } catch (MalformedURLException e) {
            log.warning("Failed to load background image", "path", path, e);
            return null;
        }
    }

    @Override // documentation inherited
    public void start ()
    {
        if (_errmsg != null) {
            _getdown.fail(_errmsg);
        } else {
            try {
                _getdown.start();
            } catch (Exception e) {
                log.warning("start() failed.", e);
            }
        }
    }

    @Override // documentation inherited
    public void stop ()
    {
        // Interrupt the getdown thread to tell it to kill its current downloading or verifying
        // before launching
        _getdown.interrupt();
        // release the lock if the applet window is closed or replaced
        _getdown._app.releaseLock();
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
                log.warning("Signed applet rejected by user", "se", se);
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
                log.warning("Couldn't parse OS version", "vers", verStr, "error", e);
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

        // if our getdown.txt file does not exist, or it is corrupt, auto-/recreate it
        File gdfile = new File(appdir, "getdown.txt");
        boolean createGetdown = !gdfile.exists();
        if (!createGetdown) {
            try {
                Map<String,Object> cdata = ConfigUtil.parseConfig(gdfile, false);
                String oappbase = StringUtil.trim((String)cdata.get("appbase"));
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
            log.warning("Failed to create '" + tofile + "'.", ioe);
            return false;
        }
    }

    /** Handles all the actual getting down. */
    protected Getdown _getdown;

    /** An error encountered during initialization. */
    protected String _errmsg;
}
