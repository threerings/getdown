//
// $Id: Differ.java,v 1.2 2004/07/13 15:52:18 ray Exp $

package com.threerings.getdown.tools;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import java.util.ArrayList;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import com.sun.javaws.jardiff.JarDiff;

import com.samskivert.io.StreamUtil;

import com.threerings.getdown.data.Application;
import com.threerings.getdown.data.Resource;

/**
 * Generates patch files between two particular revisions of an
 * application. The differences between all the files in the two
 * revisions are bundled into a single patch file which is placed into the
 * target version directory.
 */
public class Differ
{
    /** A suffix appended to file names to indicate that a file should be
     * newly created. */
    public static final String CREATE = ".create";

    /** A suffix appended to file names to indicate that a file should be
     * patched. */
    public static final String PATCH = ".patch";

    /** A suffix appended to file names to indicate that a file should be
     * deleted. */
    public static final String DELETE = ".delete";

    /**
     * Creates a single patch file that contains the differences between
     * the two specified application directories. The patch file will be
     * created in the <code>nvdir</code> directory with name
     * <code>patchV.dat</code> where V is the old application version.
     */
    public void createDiff (File nvdir, File ovdir, boolean verbose)
    {
        // sanity check
        String nvers = nvdir.getName();
        String overs = ovdir.getName();
        try {
            if (Integer.parseInt(nvers) <= Integer.parseInt(overs)) {
                System.err.println("New version (" + nvers + ") must be " +
                                   "greater than old version (" + overs + ").");
                System.exit(-1);
            }
        } catch (Exception e) {
            System.err.println("Non-numeric versions? [nvers=" + nvers +
                               ", overs=" + overs + "].");
            System.exit(-1);
        }

        Application oapp = createApplication(ovdir);
        ArrayList orsrcs = new ArrayList();
        orsrcs.addAll(oapp.getCodeResources());
        orsrcs.addAll(oapp.getResources());
        if (verbose) {
            System.out.println(orsrcs.size() + " old resources.");
        }

        Application napp = createApplication(nvdir);
        ArrayList nrsrcs = new ArrayList();
        nrsrcs.addAll(napp.getCodeResources());
        nrsrcs.addAll(napp.getResources());
        if (verbose) {
            System.out.println(nrsrcs.size() + " new resources.");
        }

        File patch = new File(nvdir, "patch" + overs + ".dat");
        JarOutputStream jout = null;
        try {
            jout = new JarOutputStream(
                new BufferedOutputStream(new FileOutputStream(patch)));

            // for each file in the new application, it either already
            // exists in the old application, or it is new
            for (int ii = 0; ii < nrsrcs.size(); ii++) {
                Resource rsrc = (Resource)nrsrcs.get(ii);
                int oidx = orsrcs.indexOf(rsrc);
                Resource orsrc = (oidx == -1) ?
                    null : (Resource)orsrcs.remove(oidx);
                if (orsrc != null && rsrc.getPath().endsWith(".jar")) {
                    if (verbose) {
                        System.out.println(
                            "Generating jardiff: " + rsrc.getPath());
                    }
                    jout.putNextEntry(new ZipEntry(rsrc.getPath() + PATCH));
                    jarDiff(orsrc.getLocal(), rsrc.getLocal(), jout);
                } else {
                    if (verbose) {
                        System.out.println("New entry: " + rsrc.getPath());
                    }
                    jout.putNextEntry(new ZipEntry(rsrc.getPath() + CREATE));
                    pipe(rsrc.getLocal(), jout);
                }
            }

            // now any file remaining in orsrcs needs to be removed
            for (int ii = 0; ii < orsrcs.size(); ii++) {
                Resource rsrc = (Resource)orsrcs.get(ii);
                // simply add an entry with the resource name and the
                // deletion suffix
                if (verbose) {
                    System.out.println("Removing entry: " + rsrc.getPath());
                }
                jout.putNextEntry(new ZipEntry(rsrc.getPath() + DELETE));
            }

            StreamUtil.close(jout);

        } catch (Exception ioe) {
            System.err.println("Failure creating patch file [patch=" + patch +
                               ", error=" + ioe + "].");
            StreamUtil.close(jout);
            patch.delete();
        }
    }

    protected Application createApplication (File dir)
    {
        Application app = new Application(dir);
        try {
            app.init();
        } catch (IOException ioe) {
            System.err.println("Invalid app directory '" + dir + "': " + ioe);
            System.exit(-1);
        }
        return app;
    }

    protected void pipe (File file, JarOutputStream jout)
        throws IOException
    {
        FileInputStream fin = null;
        try {
            fin = new FileInputStream(file);
            byte[] buffer = new byte[4096];
            int read;
            while ((read = fin.read(buffer)) >= 0) {
                jout.write(buffer, 0, read);
            }
        } finally {
            StreamUtil.close(fin);
        }
    }

    protected void jarDiff (File ofile, File nfile, JarOutputStream jout)
        throws IOException
    {
        JarDiff.createPatch(ofile.getPath(), nfile.getPath(), jout, false);
    }

    public static void main (String[] args)
    {
        if (args.length != 2) {
            System.err.println("Usage: Differ new_vers_dir old_vers_dir");
            System.exit(-1);
        }
        Differ differ = new Differ();
        differ.createDiff(new File(args[0]), new File(args[1]), false);
    }
}
