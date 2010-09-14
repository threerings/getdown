//
// $Id$
//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2010 Three Rings Design, Inc.
//
// Redistribution and use in source and binary forms, with or without modification, are permitted
// provided that the following conditions are met:
//
// 1. Redistributions of source code must retain the above copyright notice, this list of
//    conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright notice, this list of
//    conditions and the following disclaimer in the documentation and/or other materials provided
//    with the distribution.
//
// THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES,
// INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
// PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT,
// INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
// TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
// LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
// SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

package com.threerings.getdown.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.samskivert.util.StringUtil;

import static com.threerings.getdown.Log.log;

/**
 * Parses a file containing key/value pairs and returns a {@link HashMap} with the values. Keys may
 * be repeated, in which case they will be made to reference an array of values.
 */
public class ConfigUtil
{
    /**
     * Parses a configuration file containing key/value pairs. The file must be in the UTF-8
     * encoding.
     *
     * @param checkPlatform if true, platform qualifiers will be used to filter out pairs that do
     * not match the current platform; if false, all pairs will be returned.
     *
     * @return a list of <code>String[]</code> instances containing the key/value pairs in the
     * order they were parsed from the file.
     */
    public static List<String[]> parsePairs (File config, boolean checkPlatform)
        throws IOException
    {
        // annoyingly FileReader does not allow encoding to be specified (uses platform default)
        return parsePairs(
            new InputStreamReader(new FileInputStream(config), "UTF-8"), checkPlatform);
    }

    /**
     * See {@link #parsePairs(File,boolean}.
     */
    public static List<String[]> parsePairs (Reader config, boolean checkPlatform)
        throws IOException
    {
        return parsePairs(
            config,
            checkPlatform ? StringUtil.deNull(System.getProperty("os.name")).toLowerCase() : null,
            checkPlatform ? StringUtil.deNull(System.getProperty("os.arch")).toLowerCase() : null);
    }

    /**
     * Parses a configuration file containing key/value pairs. The file must be in the UTF-8
     * encoding.
     *
     * @return a map from keys to values, where a value will be an array of strings if more than
     * one key/value pair in the config file was associated with the same key.
     */
    public static HashMap<String, Object> parseConfig (File config, boolean checkPlatform)
        throws IOException
    {
        HashMap<String, Object> data = new HashMap<String, Object>();

        // I thought that we could use HashMap<String, String[]> and put new String[] {pair[1]} for
        // the null case, but it mysteriously dies on launch, so leaving it as HashMap<String,
        // Object> for now
        for (String[] pair : parsePairs(config, checkPlatform)) {
            Object value = data.get(pair[0]);
            if (value == null) {
                data.put(pair[0], pair[1]);
            } else if (value instanceof String) {
                data.put(pair[0], new String[] { (String)value, pair[1] });
            } else if (value instanceof String[]) {
                String[] values = (String[])value;
                String[] nvalues = new String[values.length+1];
                System.arraycopy(values, 0, nvalues, 0, values.length);
                nvalues[values.length] = pair[1];
                data.put(pair[0], nvalues);
            }
        }

        return data;
    }

    /**
     * Massages a single string into an array and leaves existing array values as is. Simplifies
     * access to parameters that are expected to be arrays.
     */
    public static String[] getMultiValue (HashMap<String, Object> data, String name)
    {
        Object value = data.get(name);
        if (value instanceof String) {
            return new String[] { (String)value };
        } else {
            return (String[])value;
        }
    }

    /** A helper function for {@link #parsePairs(Reader,boolean}. */
    protected static List<String[]> parsePairs (Reader config, String osname, String osarch)
        throws IOException
    {
        List<String[]> pairs = new ArrayList<String[]>();
        for (String line : FileUtil.readLines(config)) {
            // nix comments
            int cidx = line.indexOf("#");
            if (cidx != -1) {
                line = line.substring(0, cidx);
            }

            // trim whitespace and skip blank lines
            line = line.trim();
            if (StringUtil.isBlank(line)) {
                continue;
            }

            // parse our key/value pair
            String[] pair = new String[2];
            int eidx = line.indexOf("=");
            if (eidx != -1) {
                pair[0] = line.substring(0, eidx).trim();
                pair[1] = line.substring(eidx+1).trim();
            } else {
                pair[0] = line;
                pair[1] = "";
            }

            // if the pair has an os qualifier, we need to process it
            if (pair[1].startsWith("[")) {
                int qidx = pair[1].indexOf("]");
                if (qidx == -1) {
                    log.warning("Bogus platform specifier", "key", pair[0], "value", pair[1]);
                    continue; // omit the pair entirely
                }
                // if we're checking qualifiers and the os doesn't match this qualifier, skip it
                String quals = pair[1].substring(1, qidx);
                if (osname != null && !checkQualifiers(quals, osname, osarch)) {
                    log.info("Skipping", "quals", quals, "osname", osname, "osarch", osarch,
                             "key", pair[0], "value", pair[1]);
                    continue;
                }
                // otherwise filter out the qualifier text
                pair[1] = pair[1].substring(qidx+1).trim();
            }

            pairs.add(pair);
        }

        return pairs;
    }

    /**
     * A helper function for {@link #parsePairs(Reader,String,String)}. Qualifiers have the
     * following form:
     * <pre>
     * id = os[-arch]
     * ids = id | id,ids
     * quals = !id | ids
     * </pre>
     * Examples: [linux-amd64,linux-x86_64], [windows], [mac os x], [!windows]. Negative qualifiers
     * must appear alone, they cannot be used with other qualifiers (positive or negative).
     */
    protected static boolean checkQualifiers (String quals, String osname, String osarch)
    {
        if (quals.startsWith("!")) {
            if (quals.indexOf(",") != -1) { // sanity check
                log.warning("Multiple qualifiers cannot be used when one of the qualifiers " +
                            "is negative", "qual", quals);
                return false;
            }
            return !checkQualifier(quals.substring(1), osname, osarch);
        }
        for (String qual : quals.split(",")) {
            if (checkQualifier(qual, osname, osarch)) {
                return true; // if we have a positive match, we can immediately return true
            }
        }
        return false; // we had no positive matches, so return false
    }

    /** A helper function for {@link #checkQualifiers}. */
    protected static boolean checkQualifier (String qual, String osname, String osarch)
    {
        String[] bits = qual.trim().toLowerCase().split("-");
        String os = bits[0], arch = (bits.length > 1) ? bits[1] : "";
        return (osname.indexOf(os) != -1) && (osarch.indexOf(arch) != -1);
    }
}
