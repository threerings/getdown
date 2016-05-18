//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2016 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.util;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarOutputStream;
import java.util.jar.Pack200;
import java.util.zip.GZIPInputStream;

import com.samskivert.io.StreamUtil;

import static com.threerings.getdown.Log.log;

/**
 * File related utilities.
 */
public class FileUtil extends com.samskivert.util.FileUtil
{
    /**
     * Gets the specified source file to the specified destination file by hook or crook. Windows
     * has all sorts of problems which we work around in this method.
     *
     * @return true if we managed to get the job done, false otherwise.
     */
    public static boolean renameTo (File source, File dest)
    {
        // if we're on a civilized operating system we may be able to simple rename it
        if (source.renameTo(dest)) {
            return true;
        }

        // fall back to trying to rename the old file out of the way, rename the new file into
        // place and then delete the old file
        if (dest.exists()) {
            File temp = new File(dest.getPath() + "_old");
            if (temp.exists() && !temp.delete()) {
                log.warning("Failed to delete old intermediate file " + temp + ".");
                // the subsequent code will probably fail
            }
            if (dest.renameTo(temp) && source.renameTo(dest)) {
                if (!temp.delete()) {
                    log.warning("Failed to delete intermediate file " + temp + ".");
                }
                return true;
            }
        }

        // as a last resort, try copying the old data over the new
        FileInputStream fin = null;
        FileOutputStream fout = null;
        try {
            fin = new FileInputStream(source);
            fout = new FileOutputStream(dest);
            StreamUtil.copy(fin, fout);
            if (!source.delete()) {
                log.warning("Failed to delete " + source +
                            " after brute force copy to " + dest + ".");
            }
            return true;

        } catch (IOException ioe) {
            log.warning("Failed to copy " + source + " to " + dest + ": " + ioe);
            return false;

        } finally {
            StreamUtil.close(fin);
            StreamUtil.close(fout);
        }
    }

    /**
     * Reads the contents of the supplied input stream into a list of lines. Closes the reader on
     * successful or failed completion.
     */
    public static List<String> readLines (Reader in)
        throws IOException
    {
        List<String> lines = new ArrayList<String>();
        try {
            BufferedReader bin = new BufferedReader(in);
            for (String line = null; (line = bin.readLine()) != null; lines.add(line)) {}
        } finally {
            StreamUtil.close(in);
        }
        return lines;
    }

    /**
     * Unpacks a pack200 packed jar file from {@code packedJar} into {@code target}. If {@code
     * packedJar} has a {@code .gz} extension, it will be gunzipped first.
     */
    public static boolean unpackPacked200Jar (File packedJar, File target)
    {
        InputStream packedJarIn = null;
        FileOutputStream extractedJarFileOut = null;
        JarOutputStream jarOutputStream = null;
        try {
            extractedJarFileOut = new FileOutputStream(target);
            jarOutputStream = new JarOutputStream(extractedJarFileOut);
            packedJarIn = new FileInputStream(packedJar);
            if (packedJar.getName().endsWith(".gz")) {
                packedJarIn = new GZIPInputStream(packedJarIn);
            }
            Pack200.Unpacker unpacker = Pack200.newUnpacker();
            unpacker.unpack(packedJarIn, jarOutputStream);
            return true;

        } catch (IOException e) {
            log.warning("Failed to unpack packed 200 jar file", "jar", packedJar, "error", e);
            return false;

        } finally {
            StreamUtil.close(jarOutputStream);
            StreamUtil.close(extractedJarFileOut);
            StreamUtil.close(packedJarIn);
        }
    }
}
