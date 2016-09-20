package com.threerings.getdown.util.file;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Class under test: {@link FileWalker}.
 */
public class FileWalkerTest
{
    @Test
    public void shouldRecursivelyWalkOverFilesAndFolders () throws IOException
    {
        _folder.newFile("a.txt");
        new File(_folder.newFolder("b"), "b.txt").createNewFile();

        CountingVisitor visitor = new CountingVisitor();
        new FileWalker(_folder.getRoot()).walkTree(visitor);

        assertEquals(3, visitor.fileCount);
    }

    public static class CountingVisitor implements FileVisitor
    {
        int fileCount = 0;

        @Override
        public void visit(File file) {
            fileCount++;
        }
    }

    @Rule
    public TemporaryFolder _folder = new TemporaryFolder();
}
