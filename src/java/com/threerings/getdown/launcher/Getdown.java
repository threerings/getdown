//
// $Id: Getdown.java,v 1.1 2004/07/02 11:01:21 mdb Exp $

package com.threerings.getdown.launcher;

import java.io.File;

import com.threerings.getdown.Log;
import com.threerings.getdown.data.Application;

/**
 * Manages the main control for the Getdown application updater and
 * deployment system.
 */
public class Getdown
{
    public Getdown (File appDir)
    {
        _app = new Application(appDir);
    }

    public void run ()
    {
        try {
            _app.init();
        } catch (Exception e) {
            Log.logStackTrace(e);
        }
    }

    public static void main (String[] args)
    {
        // ensure the proper parameters are passed
        if (args.length != 1) {
            System.err.println("Usage: java -jar getdown.jar app_dir");
            System.exit(-1);
        }

        // ensure a valid directory was supplied
        File appDir = new File(args[0]);
        if (!appDir.exists() || !appDir.isDirectory()) {
            Log.warning("Invalid app_dir '" + args[0] + "'.");
            System.exit(-1);
        }

        try {
            Getdown app = new Getdown(appDir);
            app.run();
        } catch (Exception e) {
            Log.logStackTrace(e);
        }
    }

    protected Application _app;
}
