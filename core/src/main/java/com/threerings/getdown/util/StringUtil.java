//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2018 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.util;

import java.util.StringTokenizer;

public class StringUtil {

    /**
     * @return true if the specified string could be a valid URL (contains no illegal characters)
     */
    public static boolean couldBeValidUrl (String url)
    {
        return url.matches("[A-Za-z0-9\\-\\._~:/\\?#\\[\\]@!$&'\\(\\)\\*\\+,;=%]+");
    }

    /**
     * @return true if the string is null or consists only of whitespace, false otherwise.
     */
    public static boolean isBlank (String value)
    {
        for (int ii = 0, ll = (value == null) ? 0 : value.length(); ii < ll; ii++) {
            if (!Character.isWhitespace(value.charAt(ii))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Parses an array of integers from it's string representation. The array should be represented
     * as a bare list of numbers separated by commas, for example:
     *
     * <pre>25, 17, 21, 99</pre>
     *
     * Any inability to parse the int array will result in the function returning null.
     */
    public static int[] parseIntArray (String source)
    {
        StringTokenizer tok = new StringTokenizer(source, ",");
        int[] vals = new int[tok.countTokens()];
        for (int i = 0; tok.hasMoreTokens(); i++) {
            try {
                // trim the whitespace from the token
                vals[i] = Integer.parseInt(tok.nextToken().trim());
            } catch (NumberFormatException nfe) {
                return null;
            }
        }
        return vals;
    }

    /**
     * Parses an array of strings from a single string. The array should be represented as a bare
     * list of strings separated by commas, for example:
     *
     * <pre>mary, had, a, little, lamb, and, an, escaped, comma,,</pre>
     *
     * If a comma is desired in one of the strings, it should be escaped by putting two commas in a
     * row. Any inability to parse the string array will result in the function returning null.
     */
    public static String[] parseStringArray (String source)
    {
        return parseStringArray(source, false);
    }

    /**
     * Like {@link #parseStringArray(String)} but can be instructed to invoke {@link String#intern}
     * on the strings being parsed into the array.
     */
    public static String[] parseStringArray (String source, boolean intern)
    {
        int tcount = 0, tpos = -1, tstart = 0;

        // empty strings result in zero length arrays
        if (source.length() == 0) {
            return new String[0];
        }

        // sort out escaped commas
        source = source.replace(",,", "%COMMA%");

        // count up the number of tokens
        while ((tpos = source.indexOf(",", tpos+1)) != -1) {
            tcount++;
        }

        String[] tokens = new String[tcount+1];
        tpos = -1; tcount = 0;

        // do the split
        while ((tpos = source.indexOf(",", tpos+1)) != -1) {
            tokens[tcount] = source.substring(tstart, tpos);
            tokens[tcount] = tokens[tcount].trim().replace("%COMMA%", ",");
            if (intern) {
                tokens[tcount] = tokens[tcount].intern();
            }
            tstart = tpos+1;
            tcount++;
        }

        // grab the last token
        tokens[tcount] = source.substring(tstart);
        tokens[tcount] = tokens[tcount].trim().replace("%COMMA%", ",");

        return tokens;
    }

    /**
     * @return the supplied string if it is non-null, "" if it is null.
     */
    public static String deNull (String value)
    {
        return (value == null) ? "" : value;
    }

    /**
     * Generates a string from the supplied bytes that is the HEX encoded representation of those
     * bytes.  Returns the empty string for a <code>null</code> or empty byte array.
     *
     * @param bytes the bytes for which we want a string representation.
     * @param count the number of bytes to stop at (which will be coerced into being {@code <=} the
     * length of the array).
     */
    public static String hexlate (byte[] bytes, int count)
    {
        if (bytes == null) {
            return "";
        }

        count = Math.min(count, bytes.length);
        char[] chars = new char[count*2];

        for (int i = 0; i < count; i++) {
            int val = bytes[i];
            if (val < 0) {
                val += 256;
            }
            chars[2*i] = XLATE.charAt(val/16);
            chars[2*i+1] = XLATE.charAt(val%16);
        }

        return new String(chars);
    }

    /**
     * Generates a string from the supplied bytes that is the HEX encoded representation of those
     * bytes.
     */
    public static String hexlate (byte[] bytes)
    {
        return (bytes == null) ? "" : hexlate(bytes, bytes.length);
    }

    /**
     * Joins an array of strings (or objects which will be converted to strings) into a single
     * string separated by commas.
     */
    public static String join (Object[] values)
    {
        return join(values, false);
    }

    /**
     * Joins an array of strings into a single string, separated by commas, and optionally escaping
     * commas that occur in the individual string values such that a subsequent call to {@link
     * #parseStringArray} would recreate the string array properly. Any elements in the values
     * array that are null will be treated as an empty string.
     */
    public static String join (Object[] values, boolean escape)
    {
        return join(values, ", ", escape);
    }

    /**
     * Joins the supplied array of strings into a single string separated by the supplied
     * separator.
     */
    public static String join (Object[] values, String separator)
    {
        return join(values, separator, false);
    }

    /**
     * Helper function for the various <code>join</code> methods.
     */
    protected static String join (Object[] values, String separator, boolean escape)
    {
        StringBuilder buf = new StringBuilder();
        int vlength = values.length;
        for (int i = 0; i < vlength; i++) {
            if (i > 0) {
                buf.append(separator);
            }
            String value = (values[i] == null) ? "" : values[i].toString();
            buf.append((escape) ? value.replace(",", ",,") : value);
        }
        return buf.toString();
    }

    /** Used by {@link #hexlate} and {@link #unhexlate}. */
    protected static final String XLATE = "0123456789abcdef";
}
