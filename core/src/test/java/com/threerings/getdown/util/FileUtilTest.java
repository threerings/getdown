//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2018 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.util;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

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

    @Test public void shouldCopyFile () throws IOException
    {
        File source = _folder.newFile("source.txt");
        File target = new File(_folder.getRoot(), "target.txt");
        assertFalse(target.exists());
        FileUtil.copy(source, target);
        assertTrue(target.exists());
    }

    @Test public void shouldRecursivelyWalkOverFilesAndFolders () throws IOException
    {
        _folder.newFile("a.txt");
        new File(_folder.newFolder("b"), "b.txt").createNewFile();

        class CountingVisitor implements FileUtil.Visitor {
            int fileCount = 0;
            @Override public void visit(File file) {
                fileCount++;
            }
        }
        CountingVisitor visitor = new CountingVisitor();
        FileUtil.walkTree(_folder.getRoot(), visitor);
        assertEquals(3, visitor.fileCount);
    }

    @Rule public TemporaryFolder _folder = new TemporaryFolder();
}
