//
// $Id: ConfigUtil.java,v 1.1 2004/07/02 11:01:21 mdb Exp $

package com.threerings.getdown.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import com.samskivert.util.StringUtil;

/**
 * Parses a file containing key/value pairs and returns a {@link HashMap}
 * with the values. Keys may be repeated, in which case they will be made
 * to reference an array of values.
 */
public class ConfigUtil
{
    /**
     * Parses a configuration file containing key/value pairs. The file
     * must be in the UTF-8 encoding.
     *
     * @return a list of <code>String[]</code> instances containing the
     * key/value pairs in the order they were parsed from the file.
     */
    public static List parsePairs (File config)
        throws IOException
    {
        ArrayList pairs = new ArrayList();

        // parse our configuration file
        BufferedReader bin = new BufferedReader(
            new InputStreamReader(new FileInputStream(config), "UTF-8"));
        String line = null;
        while ((line = bin.readLine()) != null) {
            // nix comments
            int cidx = line.indexOf("#");
            if (cidx != -1) {
                line = line.substring(0, cidx);
            }

            // trim whitespace and skip blank lines
            line = line.trim();
            if (StringUtil.blank(line)) {
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

            pairs.add(pair);
        }

        return pairs;
    }

    /**
     * Parses a configuration file containing key/value pairs. The file
     * must be in the UTF-8 encoding.
     *
     * @return a map from keys to values, where a value will be an array
     * of strings if more than one key/value pair in the config file was
     * associated with the same key.
     */
    public static HashMap parseConfig (File config)
        throws IOException
    {
        List pairs = parsePairs(config);
        HashMap data = new HashMap();

        for (Iterator iter = pairs.iterator(); iter.hasNext(); ) {
            String[] pair = (String[])iter.next();
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
     * Massages a single string into an array and leaves existing array
     * values as is. Simplifies access to parameters that are expected to
     * be arrays.
     */
    public static String[] getMultiValue (HashMap data, String name)
    {
        Object value = data.get(name);
        if (value instanceof String) {
            return new String[] { (String)value };
        } else {
            return (String[])value;
        }
    }
}
