//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2018 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.tools;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;

import java.security.MessageDigest;

import com.threerings.getdown.data.Application;
import com.threerings.getdown.data.Digest;
import com.threerings.getdown.data.EnvConfig;
import com.threerings.getdown.data.Resource;
import com.threerings.getdown.util.FileUtil;
import com.threerings.getdown.util.StreamUtil;

/**
 * Generates patch files between two particular revisions of an
 * application. The differences between all the files in the two
 * revisions are bundled into a single patch file which is placed into the
 * target version directory.
 */
public class Differ
{
    /**
     * Creates a single patch file that contains the differences between
     * the two specified application directories. The patch file will be
     * created in the <code>nvdir</code> directory with name
     * <code>patchV.dat</code> where V is the old application version.
     */
    public void createDiff (File nvdir, File ovdir, boolean verbose)
        throws IOException
    {
        // sanity check
        String nvers = nvdir.getName();
        String overs = ovdir.getName();
        try {
            if (Long.parseLong(nvers) <= Long.parseLong(overs)) {
                String err = "New version (" + nvers + ") must be greater " +
                    "than old version (" + overs + ").";
                throw new IOException(err);
            }
        } catch (NumberFormatException nfe) {
            throw new IOException("Non-numeric versions? [nvers=" + nvers +
                                  ", overs=" + overs + "].");
        }

        Application oapp = new Application(new EnvConfig(ovdir));
        oapp.init(false);
        ArrayList<Resource> orsrcs = new ArrayList<>();
        orsrcs.addAll(oapp.getCodeResources());
        orsrcs.addAll(oapp.getResources());

        Application napp = new Application(new EnvConfig(nvdir));
        napp.init(false);
        ArrayList<Resource> nrsrcs = new ArrayList<>();
        nrsrcs.addAll(napp.getCodeResources());
        nrsrcs.addAll(napp.getResources());

        // first create a patch for the main application
        File patch = new File(nvdir, "patch" + overs + ".dat");
        createPatch(patch, orsrcs, nrsrcs, verbose);

        // next create patches for any auxiliary resource groups
        for (Application.AuxGroup ag : napp.getAuxGroups()) {
            orsrcs = new ArrayList<>();
            Application.AuxGroup oag = oapp.getAuxGroup(ag.name);
            if (oag != null) {
                orsrcs.addAll(oag.codes);
                orsrcs.addAll(oag.rsrcs);
            }
            nrsrcs = new ArrayList<>();
            nrsrcs.addAll(ag.codes);
            nrsrcs.addAll(ag.rsrcs);
            patch = new File(nvdir, "patch-" + ag.name + overs + ".dat");
            createPatch(patch, orsrcs, nrsrcs, verbose);
        }
    }

    protected void createPatch (File patch, ArrayList<Resource> orsrcs,
                                ArrayList<Resource> nrsrcs, boolean verbose)
        throws IOException
    {
        int version = Digest.VERSION;
        MessageDigest md = Digest.getMessageDigest(version);
        try (FileOutputStream fos = new FileOutputStream(patch);
             BufferedOutputStream buffered = new BufferedOutputStream(fos);
             JarOutputStream jout = new JarOutputStream(buffered)) {

            // for each file in the new application, it either already exists
            // in the old application, or it is new
            for (Resource rsrc : nrsrcs) {
                int oidx = orsrcs.indexOf(rsrc);
                Resource orsrc = (oidx == -1) ? null : orsrcs.remove(oidx);
                if (orsrc != null) {
                    // first see if they are the same
                    String odig = orsrc.computeDigest(version, md, null);
                    String ndig = rsrc.computeDigest(version, md, null);
                    if (odig.equals(ndig)) {
                        if (verbose) {
                            System.out.println("Unchanged: " + rsrc.getPath());
                        }
                        // by leaving it out, it will be left as is during the
                        // patching process
                        continue;
                    }

                    // otherwise potentially create a jar diff
                    if (rsrc.getPath().endsWith(".jar")) {
                        if (verbose) {
                            System.out.println("JarDiff: " + rsrc.getPath());
                        }
                        // here's a juicy one: JarDiff blindly pulls ZipEntry
                        // objects out of one jar file and stuffs them into
                        // another without clearing out things like the
                        // compressed size, so if, for whatever reason (like
                        // different JRE versions or phase of the moon) the
                        // compressed size in the old jar file is different
                        // than the compressed size generated when creating the
                        // jardiff jar file, ZipOutputStream will choke and
                        // we'll be hosed; so we recreate the jar files in
                        // their entirety before running jardiff on 'em
                        File otemp = rebuildJar(orsrc.getLocal());
                        File temp = rebuildJar(rsrc.getLocal());
                        jout.putNextEntry(new ZipEntry(rsrc.getPath() + Patcher.PATCH));
                        jarDiff(otemp, temp, jout);
                        FileUtil.deleteHarder(otemp);
                        FileUtil.deleteHarder(temp);
                        continue;
                    }
                }

                if (verbose) {
                    System.out.println("Addition: " + rsrc.getPath());
                }
                jout.putNextEntry(new ZipEntry(rsrc.getPath() + Patcher.CREATE));
                pipe(rsrc.getLocal(), jout);
            }

            // now any file remaining in orsrcs needs to be removed
            for (Resource rsrc : orsrcs) {
                // add an entry with the resource name and the deletion suffix
                if (verbose) {
                    System.out.println("Removal: " + rsrc.getPath());
                }
                jout.putNextEntry(new ZipEntry(rsrc.getPath() + Patcher.DELETE));
            }

            System.out.println("Created patch file: " + patch);

        } catch (IOException ioe) {
            FileUtil.deleteHarder(patch);
            throw ioe;
        }
    }

    protected File rebuildJar (File target)
        throws IOException
    {
        File temp = File.createTempFile("differ", "jar");
        try (JarFile jar = new JarFile(target);
             FileOutputStream tempFos = new FileOutputStream(temp);
             BufferedOutputStream tempBos = new BufferedOutputStream(tempFos);
             JarOutputStream jout = new JarOutputStream(tempBos)) {
            byte[] buffer = new byte[4096];
            for (Enumeration< JarEntry > iter = jar.entries(); iter.hasMoreElements();) {
                JarEntry entry = iter.nextElement();
                entry.setCompressedSize(-1);
                jout.putNextEntry(entry);
                try (InputStream in = jar.getInputStream(entry)) {
                    int size = in.read(buffer);
                    while (size != -1) {
                        jout.write(buffer, 0, size);
                        size = in.read(buffer);
                    }
                }
            }
        }
        return temp;
    }

    protected void jarDiff (File ofile, File nfile, JarOutputStream jout)
        throws IOException
    {
        JarDiff.createPatch(ofile.getPath(), nfile.getPath(), jout, false);
    }

    public static void main (String[] args)
    {
        if (args.length < 2) {
            System.err.println(
                "Usage: Differ [-verbose] new_vers_dir old_vers_dir");
            System.exit(255);
        }
        Differ differ = new Differ();
        boolean verbose = false;
        int aidx = 0;
        if (args[0].equals("-verbose")) {
            verbose = true;
            aidx++;
        }
        try {
            differ.createDiff(new File(args[aidx++]),
                              new File(args[aidx++]), verbose);
        } catch (IOException ioe) {
            System.err.println("Error: " + ioe.getMessage());
            System.exit(255);
        }
    }

    protected static void pipe (File file, JarOutputStream jout)
        throws IOException
    {
        try (FileInputStream fin = new FileInputStream(file)) {
            StreamUtil.copy(fin, jout);
        }
    }
}
