//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2014 Three Rings Design, Inc.
// https://raw.github.com/threerings/getdown/master/LICENSE

package com.threerings.getdown.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;

import com.samskivert.io.StreamUtil;
import com.samskivert.util.StringUtil;

import static com.threerings.getdown.Log.log;

/**
 * Version related utilities.
 */
public class VersionUtil
{
    /**
     * Reads a version number from a file.
     */
    public static long readVersion (File vfile)
    {
        FileInputStream fin = null;
        long fileVersion = -1;
        try {
            fin = new FileInputStream(vfile);
            BufferedReader bin = new BufferedReader(new InputStreamReader(fin));
            String vstr = bin.readLine();
            if (!StringUtil.isBlank(vstr)) {
                fileVersion = Long.parseLong(vstr);
            }
        } catch (Exception e) {
            log.info("Unable to read version file: " + e.getMessage());
        } finally {
            StreamUtil.close(fin);
        }

        return fileVersion;
    }

    /**
     * Writes a version number to a file.
     */
    public static void writeVersion (File vfile, long version)
        throws IOException
    {
        PrintStream out = new PrintStream(new FileOutputStream(vfile));
        try {
            out.println(version);
        } catch (Exception e) {
            log.warning("Unable to write version file: " + e.getMessage());
        } finally {
            StreamUtil.close(out);
        }
    }
}
