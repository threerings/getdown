//
// $Id: Getdown.java,v 1.3 2004/07/02 17:03:33 mdb Exp $

package com.threerings.getdown.launcher;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

import java.util.List;

import org.apache.commons.io.TeeOutputStream;

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

            if (_app.verifyMetadata()) {
                Log.info("Application requires update.");

            } else {
                Log.info("Metadata verified.");
                List failures = _app.verifyResources();
                if (failures == null) {
                    Log.info("Resources verified.");
                } else {
                    Log.info(failures.size() + " resources require update.");
                }
            }

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

//         // tee our output into a file in the application directory
//         File log = new File(appDir, "getdown.log");
//         try {
//             FileOutputStream fout = new FileOutputStream(log);
//             System.setOut(new PrintStream(
//                               new TeeOutputStream(System.out, fout)));
//             System.setErr(new PrintStream(
//                               new TeeOutputStream(System.err, fout)));
//         } catch (IOException ioe) {
//             Log.warning("Unable to redirect output to '" + log + "': " + ioe);
//         }

        try {
            Getdown app = new Getdown(appDir);
            app.run();
        } catch (Exception e) {
            Log.logStackTrace(e);
        }
    }

    protected Application _app;
}
