//
// $Id: DigesterTask.java,v 1.1 2004/07/02 11:01:21 mdb Exp $

package com.threerings.getdown.tools;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;

import com.samskivert.util.CollectionUtil;

import com.threerings.getdown.data.Application;
import com.threerings.getdown.data.Digest;

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
        File target = new File(_appdir, Digest.DIGEST_FILE);
        System.out.println("Generating digest file '" + target + "'...");

        // create our application and instruct it to parse its business
        Application app = new Application(_appdir);
        try {
            app.init();
        } catch (IOException ioe) {
            throw new BuildException("Error parsing getdown.txt: " +
                                     ioe.getMessage(), ioe);
        }

        ArrayList rsrcs = new ArrayList();
        rsrcs.add(app.getConfigResource());
        CollectionUtil.addAll(rsrcs, app.getCodeResources().iterator());
        CollectionUtil.addAll(rsrcs, app.getResources().iterator());

        // now generate the digest file
        try {
            Digest.createDigest(rsrcs, target);
        } catch (IOException ioe) {
            throw new BuildException("Error creating digest: " +
                                     ioe.getMessage(), ioe);
        }
    }

    /** The application directory in which we're creating a digest file. */
    protected File _appdir;
}
