//
// $Id: Patcher.java,v 1.1 2004/07/13 17:45:40 mdb Exp $

package com.threerings.getdown.tools;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import java.util.Enumeration;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import com.samskivert.io.StreamUtil;
import com.sun.javaws.jardiff.JarDiffPatcher;
import org.apache.commons.io.CopyUtils;

/**
 * Applies a unified patch file to an application directory, providing
 * percentage completion feedback along the way.
 */
public class Patcher
{
    /** Used to communicate patching progress. */
    public static interface Observer
    {
        /** Informs the observer that we have completed the specified
         * percentage of the patching process. */
        public void progress (int percent);
    }

    /**
     * Applies the specified patch file to the application living in the
     * specified application directory. The supplied observer, if
     * non-null, will be notified of progress along the way.
     *
     * <p><em>Note:</em> this method runs on the calling thread, thus the
     * caller may want to make use of a separate thread in conjunction
     * with the patcher so that the user interface is not blocked for the
     * duration of the patch.
     */
    public void patch (File appdir, File patch, Observer obs)
        throws IOException
    {
        JarFile file = new JarFile(patch);
        Enumeration entries = file.entries(); // old skool!
        while (entries.hasMoreElements()) {
            ZipEntry entry = (ZipEntry)entries.nextElement();
            String path = entry.getName();

            // depending on the suffix, we do The Right Thing (tm)
            if (path.endsWith(Differ.CREATE)) {
                path = strip(path, Differ.CREATE);
                System.out.println("Creating " + path + "...");
                createFile(file, entry, new File(appdir, path));

            } else if (path.endsWith(Differ.PATCH)) {
                path = strip(path, Differ.PATCH);
                System.out.println("Patching " + path + "...");
                patchFile(file, entry, appdir, path);

            } else if (path.endsWith(Differ.DELETE)) {
                path = strip(path, Differ.DELETE);
                System.out.println("Removing " + path + "...");
                File target = new File(appdir, path);
                if (!target.delete()) {
                    System.err.println("Failure deleting '" + target + "'.");
                }

            } else {
                System.err.println("Skipping bogus patch file entry: " + path);
            }
        }
    }

    protected String strip (String path, String suffix)
    {
        return path.substring(0, path.length() - suffix.length());
    }

    protected void createFile (JarFile file, ZipEntry entry, File target)
    {
        InputStream in = null;
        FileOutputStream fout = null;
        try {
            CopyUtils.copy(in = file.getInputStream(entry),
                           fout = new FileOutputStream(target));

        } catch (IOException ioe) {
            System.err.println("Error creating '" + target + "': " + ioe);

        } finally {
            StreamUtil.close(in);
            StreamUtil.close(fout);
        }
    }

    protected void patchFile (JarFile file, ZipEntry entry,
                              File appdir, String path)
    {
        File target = new File(appdir, path);
        File patch = new File(appdir, entry.getName());
        File otarget = new File(appdir, path + ".old");
        JarDiffPatcher patcher = null;

        // make sure no stale old target is lying around to mess us up
        otarget.delete();

        // pipe the contents of the patch into a file
        InputStream in = null;
        FileOutputStream fout = null;
        try {
            CopyUtils.copy(in = file.getInputStream(entry),
                           fout = new FileOutputStream(patch));
            StreamUtil.close(fout);
            fout = null;

            // move the current version of the jar to .old
            if (!target.renameTo(otarget)) {
                System.err.println("Failed to .oldify '" + target + "'.");
                return;
            }

            // now apply the patch to create the new target file
            patcher = new JarDiffPatcher();
            fout = new FileOutputStream(target);
            patcher.applyPatch(null, otarget.getPath(), patch.getPath(), fout);

        } catch (IOException ioe) {
            if (patcher == null) {
                System.err.println("Failed to write patch file '" + patch +
                                   "': " + ioe);
            } else {
                System.err.println("Error patching '" + target + "': " + ioe);
            }

        } finally {
            StreamUtil.close(fout);
            StreamUtil.close(in);
            // clean up our temporary files
            patch.delete();
            otarget.delete();
        }
    }

    public static void main (String[] args)
    {
        if (args.length != 2) {
            System.err.println("Usage: Patcher appdir patch_file");
            System.exit(-1);
        }

        Patcher patcher = new Patcher();
        try {
            patcher.patch(new File(args[0]), new File(args[1]), null);
        } catch (IOException ioe) {
            System.err.println("Error: " + ioe.getMessage());
            System.exit(-1);
        }
    }
}
