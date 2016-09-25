package com.threerings.getdown.util.file;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/**
 * Recursively walks over files and folders. A {@link FileVisitor visitor} is informed for each path
 * traversed.
 */
public class FileWalker
{
    public FileWalker (File start)
    {
        this._start = start;
    }

    /**
     * Walks over the directory tree with the provided {@code visitor}.
     */
    public void walkTree (FileVisitor visitor)
    {
        Deque<File> stack = new ArrayDeque<File>(Arrays.asList(_start.listFiles()));

        while (!stack.isEmpty()) {
            File currentFile = stack.pop();

            if (currentFile.exists()) {
                visitor.visit(currentFile);

                if (currentFile.isDirectory()) {
                    for (File file: currentFile.listFiles()) {
                        stack.push(file);
                    }
                }
            }
        }
    }

    private final File _start;
}
