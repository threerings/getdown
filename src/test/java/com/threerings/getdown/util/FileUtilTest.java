//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2016 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Tests {@link FileUtil}.
 */
public class FileUtilTest
{
    @Test
    public void testReadLines () throws IOException
    {
        String data = "This is a test\nof a file with\na few lines\n";
        List<String> lines = FileUtil.readLines(new StringReader(data));
        String[] linesBySplit = data.split("\n");
        assertEquals(linesBySplit.length, lines.size());
        for (int i = 0; i < lines.size(); i++) {
            assertEquals(linesBySplit[i], lines.get(i));
        }
    }

    @Test
    public void shouldCopyFile () throws IOException
    {
        File source = _folder.newFile("source.txt");
        File target = new File(_folder.getRoot(), "target.txt");

        assertFalse(target.exists());

        FileUtil.copy(source, target);

        assertTrue(target.exists());
    }

    @Rule
    public TemporaryFolder _folder = new TemporaryFolder();
}
