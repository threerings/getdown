//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2016 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.samskivert.io.StreamUtil;
import com.samskivert.util.StringUtil;

import com.threerings.getdown.data.SysProps;
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
        long fileVersion = -1;
        BufferedReader bin = null;
        try {
            bin = new BufferedReader(new InputStreamReader(new FileInputStream(vfile)));
            String vstr = bin.readLine();
            if (!StringUtil.isBlank(vstr)) {
                fileVersion = Long.parseLong(vstr);
            }
        } catch (Exception e) {
            log.info("Unable to read version file: " + e.getMessage());
        } finally {
            StreamUtil.close(bin);
        }

        return fileVersion;
    }

    /**
     * Writes a version number to a file.
     */
    public static void writeVersion (File vfile, long version) throws IOException
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

    /**
     * Parses {@code versStr} using {@code versRegex} into a (long) integer version number.
     * @see SysProps#parseJavaVersion
     */
    public static long parseJavaVersion (String versRegex, String versStr)
    {
        Matcher m = Pattern.compile(versRegex).matcher(versStr);
        if (!m.matches()) return 0L;

        long vers = 0L;
        for (int ii = 1; ii <= m.groupCount(); ii++) {
            String valstr = m.group(ii);
            int value = (valstr == null) ? 0 : parseInt(valstr);
            vers *= 100;
            vers += value;
        }
        return vers;
    }

    /**
     * Reads and parses the version from the {@code release} file bundled with a JVM.
     */
    public static long readReleaseVersion (File relfile, String versRegex)
    {
        BufferedReader in = null;
        try {
            in = new BufferedReader(new FileReader(relfile));
            String line = null, relvers = null;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("JAVA_VERSION=")) {
                    relvers = line.substring("JAVA_VERSION=".length()).replace('"', ' ').trim();
                }
            }

            if (relvers == null) {
                log.warning("No JAVA_VERSION line in 'release' file", "file", relfile);
                return 0L;
            }
            return parseJavaVersion(versRegex, relvers);

        } catch (Exception e) {
            log.warning("Failed to read version from 'release' file", "file", relfile, e);
            return 0L;
        } finally {
            StreamUtil.close(in);
        }
    }

    private static int parseInt (String str) {
        int value = 0;
        for (int ii = 0, ll = str.length(); ii < ll; ii++) {
            char c = str.charAt(ii);
            if (c >= '0' && c <= '9') {
                value *= 10;
                value += (c - '0');
            }
        }
        return value;
    }
}
