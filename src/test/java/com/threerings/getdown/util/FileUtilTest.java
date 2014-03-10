//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2014 Three Rings Design, Inc.
// https://raw.github.com/threerings/getdown/master/LICENSE

package com.threerings.getdown.util;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * Tests {@link FileUtil}.
 */
public class FileUtilTest
{
    @Test public void testReadLines () throws IOException
    {
        String data = "This is a test\nof a file with\na few lines\n";
        List<String> lines = FileUtil.readLines(new StringReader(data));
        String[] linesBySplit = data.split("\n");
        assertEquals(linesBySplit.length, lines.size());
        for (int ii = 0; ii < lines.size(); ii++) {
            assertEquals(linesBySplit[ii], lines.get(ii));
        }
    }
}
