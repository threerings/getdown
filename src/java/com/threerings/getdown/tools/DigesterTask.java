//
// $Id$

package com.threerings.getdown.tools;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;

import com.threerings.getdown.data.Application;
import com.threerings.getdown.data.Digest;
import com.threerings.getdown.data.Resource;

/**
 * An ant task used to create a <code>digest.txt</code> for a Getdown
 * application deployment.
 */
public class DigesterTask extends Task
{
    /**
     * Sets the application directory.
     */
    public void setAppdir (File appdir)
    {
        _appdir = appdir;
    }

    /**
     * Performs the actual work of the task.
     */
    public void execute () throws BuildException
    {
        // make sure appdir is set
        if (_appdir == null) {
            String errmsg = "Must specify the path to the application " +
                "directory  via the 'appdir' attribute.";
            throw new BuildException(errmsg);
        }

        try {
            createDigest(_appdir);
        } catch (IOException ioe) {
            throw new BuildException("Error creating digest: " +
                                     ioe.getMessage(), ioe);
        }
    }

    /**
     * Creates a digest file in the specified application directory.
     */
    public static void createDigest (File appdir)
        throws IOException
    {
        File target = new File(appdir, Digest.DIGEST_FILE);
        System.out.println("Generating digest file '" + target + "'...");

        // create our application and instruct it to parse its business
        Application app = new Application(appdir, null);
        app.init(false);

        ArrayList<Resource> rsrcs = new ArrayList<Resource>();
        rsrcs.add(app.getConfigResource());
        rsrcs.addAll(app.getCodeResources());
        rsrcs.addAll(app.getResources());

        // now generate the digest file
        Digest.createDigest(rsrcs, target);
    }

    /** The application directory in which we're creating a digest file. */
    protected File _appdir;
}
