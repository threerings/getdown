//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2014 Three Rings Design, Inc.
// https://raw.github.com/threerings/getdown/master/LICENSE

package com.threerings.getdown.util;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import com.samskivert.util.RandomUtil;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * Tests {@link ConfigUtil}.
 */
public class ConfigUtilTest
{
    public static class Pair {
        public final String key;
        public final String value;
        public Pair (String key, String value) {
            this.key = key;
            this.value = value;
        }
    }

    public static final Pair[] SIMPLE_PAIRS = {
        new Pair("one", "two"),
        new Pair("three", "four"),
        new Pair("five", "six"),
        new Pair("seven", "eight"),
        new Pair("nine", "ten"),
    };

    @Test public void testSimplePairs () throws IOException
    {
        List<String[]> pairs = ConfigUtil.parsePairs(toReader(SIMPLE_PAIRS), true);
        for (int ii = 0; ii < SIMPLE_PAIRS.length; ii++) {
            assertEquals(SIMPLE_PAIRS[ii].key, pairs.get(ii)[0]);
            assertEquals(SIMPLE_PAIRS[ii].value, pairs.get(ii)[1]);
        }
    }

    @Test public void testQualifiedPairs () throws IOException
    {
        Pair linux = new Pair("one", "[linux] two");
        Pair mac = new Pair("three", "[mac os x] four");
        Pair linuxAndMac = new Pair("five", "[linux, mac os x] six");
        Pair linux64 = new Pair("seven", "[linux-x86_64] eight");
        Pair linux64s = new Pair("nine", "[linux-x86_64, linux-amd64] ten");
        Pair mac64 = new Pair("eleven", "[mac os x-x86_64] twelve");
        Pair win64 = new Pair("thirteen", "[windows-x86_64] fourteen");
        Pair notWin = new Pair("fifteen", "[!windows] sixteen");
        Pair[] pairs = { linux, mac, linuxAndMac, linux64, linux64s, mac64, win64, notWin };

        List<String[]> parsed = ConfigUtil.parsePairs(toReader(pairs), "linux", "i386");
        assertTrue(exists(parsed, linux.key));
        assertTrue(!exists(parsed, mac.key));
        assertTrue(exists(parsed, linuxAndMac.key));
        assertTrue(!exists(parsed, linux64.key));
        assertTrue(!exists(parsed, linux64s.key));
        assertTrue(!exists(parsed, mac64.key));
        assertTrue(!exists(parsed, win64.key));
        assertTrue(exists(parsed, notWin.key));

        parsed = ConfigUtil.parsePairs(toReader(pairs), "linux", "x86_64");
        assertTrue(exists(parsed, linux.key));
        assertTrue(!exists(parsed, mac.key));
        assertTrue(exists(parsed, linuxAndMac.key));
        assertTrue(exists(parsed, linux64.key));
        assertTrue(exists(parsed, linux64s.key));
        assertTrue(!exists(parsed, mac64.key));
        assertTrue(!exists(parsed, win64.key));
        assertTrue(exists(parsed, notWin.key));

        parsed = ConfigUtil.parsePairs(toReader(pairs), "linux", "amd64");
        assertTrue(exists(parsed, linux.key));
        assertTrue(!exists(parsed, mac.key));
        assertTrue(exists(parsed, linuxAndMac.key));
        assertTrue(!exists(parsed, linux64.key));
        assertTrue(exists(parsed, linux64s.key));
        assertTrue(!exists(parsed, mac64.key));
        assertTrue(!exists(parsed, win64.key));
        assertTrue(exists(parsed, notWin.key));

        parsed = ConfigUtil.parsePairs(toReader(pairs), "mac os x", "x86_64");
        assertTrue(!exists(parsed, linux.key));
        assertTrue(exists(parsed, mac.key));
        assertTrue(exists(parsed, linuxAndMac.key));
        assertTrue(!exists(parsed, linux64.key));
        assertTrue(!exists(parsed, linux64s.key));
        assertTrue(exists(parsed, mac64.key));
        assertTrue(!exists(parsed, win64.key));
        assertTrue(exists(parsed, notWin.key));

        parsed = ConfigUtil.parsePairs(toReader(pairs), "windows", "i386");
        assertTrue(!exists(parsed, linux.key));
        assertTrue(!exists(parsed, mac.key));
        assertTrue(!exists(parsed, linuxAndMac.key));
        assertTrue(!exists(parsed, linux64.key));
        assertTrue(!exists(parsed, linux64s.key));
        assertTrue(!exists(parsed, mac64.key));
        assertTrue(!exists(parsed, win64.key));
        assertTrue(!exists(parsed, notWin.key));

        parsed = ConfigUtil.parsePairs(toReader(pairs), "windows", "x86_64");
        assertTrue(!exists(parsed, linux.key));
        assertTrue(!exists(parsed, mac.key));
        assertTrue(!exists(parsed, linuxAndMac.key));
        assertTrue(!exists(parsed, linux64.key));
        assertTrue(!exists(parsed, linux64s.key));
        assertTrue(!exists(parsed, mac64.key));
        assertTrue(exists(parsed, win64.key));
        assertTrue(!exists(parsed, notWin.key));

        parsed = ConfigUtil.parsePairs(toReader(pairs), "windows", "amd64");
        assertTrue(!exists(parsed, linux.key));
        assertTrue(!exists(parsed, mac.key));
        assertTrue(!exists(parsed, linuxAndMac.key));
        assertTrue(!exists(parsed, linux64.key));
        assertTrue(!exists(parsed, linux64s.key));
        assertTrue(!exists(parsed, mac64.key));
        assertTrue(!exists(parsed, win64.key));
        assertTrue(!exists(parsed, notWin.key));
    }

    protected static boolean exists (List<String[]> pairs, String key)
    {
        for (String[] pair : pairs) {
            if (pair[0].equals(key)) {
                return true;
            }
        }
        return false;
    }

    protected static StringReader toReader (Pair[] pairs)
    {
        StringBuilder builder = new StringBuilder();
        for (Pair pair : pairs) {
            // throw some whitespace in to ensure it's trimmed
            builder.append(whitespace()).append(pair.key).
                append(whitespace()).append("=").
                append(whitespace()).append(pair.value).
                append(whitespace()).append("\n");
        }
        return new StringReader(builder.toString());
    }

    protected static String whitespace ()
    {
        return RandomUtil.getBoolean() ? " " : "";
    }
}
