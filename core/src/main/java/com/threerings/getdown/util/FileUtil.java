//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2018 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.util;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;

import com.threerings.getdown.Log;
import static com.threerings.getdown.Log.log;

/**
 * File related utilities.
 */
public final class FileUtil
{
    /**
     * Gets the specified source file to the specified destination file by hook or crook. Windows
     * has all sorts of problems which we work around in this method.
     *
     * @return true if we managed to get the job done, false otherwise.
     */
    public static boolean renameTo (File source, File dest)
    {
        // if we're on a civilized operating system we may be able to simple rename it
        if (source.renameTo(dest)) {
            return true;
        }

        // fall back to trying to rename the old file out of the way, rename the new file into
        // place and then delete the old file
        if (dest.exists()) {
            File temp = new File(dest.getPath() + "_old");
            if (temp.exists() && !deleteHarder(temp)) {
                log.warning("Failed to delete old intermediate file " + temp + ".");
                // the subsequent code will probably fail
            }
            if (dest.renameTo(temp) && source.renameTo(dest)) {
                if (!deleteHarder(temp)) {
                    log.warning("Failed to delete intermediate file " + temp + ".");
                }
                return true;
            }
        }

        // as a last resort, try copying the old data over the new
        try {
            copy(source, dest);
        } catch (IOException ioe) {
            log.warning("Failed to copy " + source + " to " + dest + ": " + ioe);
            return false;
        }

        if (!deleteHarder(source)) {
            log.warning("Failed to delete " + source + " after brute force copy to " + dest + ".");
        }
        return true;
    }

    /**
     * "Tries harder" to delete {@code file} than just calling {@code delete} on it. Presently this
     * just means "try a second time if the first time fails, and if that fails then try to delete
     * when the virtual machine terminates." On Windows Vista, sometimes deletes fail but then
     * succeed if you just try again. Given that delete failure is a rare occurrence, we can
     * implement this hacky workaround without any negative consequences for normal behavior.
     */
    public static boolean deleteHarder (File file) {
        // if at first you don't succeed... try, try again
        boolean deleted = (file.delete() || file.delete());
        if (!deleted) {
            file.deleteOnExit();
        }
        return deleted;
    }

    /**
     * Force deletes {@code file} and all of its children recursively using {@link #deleteHarder}.
     * Note that some children may still be deleted even if {@code false} is returned. Also, since
     * {@link #deleteHarder} is used, the {@code file} could be deleted once the jvm exits even if
     * {@code false} is returned.
     *
     * @param file file to delete.
     * @return true iff {@code file} was successfully deleted.
     */
    public static boolean deleteDirHarder (File file) {
        if (file.isDirectory()) {
            for (File child : file.listFiles()) {
                deleteDirHarder(child);
            }
        }
        return deleteHarder(file);
    }

    /**
     * Reads the contents of the supplied input stream into a list of lines. Closes the reader on
     * successful or failed completion.
     */
    public static List<String> readLines (Reader in)
        throws IOException
    {
        List<String> lines = new ArrayList<>();
        try (BufferedReader bin = new BufferedReader(in)) {
            for (String line = null; (line = bin.readLine()) != null; lines.add(line)) {}
        }
        return lines;
    }

    /**
     * Unpacks the specified jar file into the specified target directory.
     * @param cleanExistingDirs if true, all files and subdirectories in all directories contained in {@code jar} will
     * be deleted prior to unpacking the jar.
     */
    public static void unpackJar (ZipFile jar, File target, boolean cleanExistingDirs)
        throws IOException
    {
        if (cleanExistingDirs) {
            Enumeration<? extends ZipEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    File efile = new File(target, entry.getName());
                    if (efile.exists()) {
                        deleteDirHarder(efile);
                    }
                }
            }
        }

        Enumeration<? extends ZipEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            File efile = new File(target, entry.getName());
            if (!efile.toPath().normalize().startsWith(target.toPath().normalize())) {
                throw new IOException("Bad zip entry");
            }

            // if we're unpacking a normal jar file, it will have special path
            // entries that allow us to create our directories first
            if (entry.isDirectory()) {
                if (!efile.exists() && !efile.mkdir()) {
                    log.warning("Failed to create jar entry path", "jar", jar, "entry", entry);
                }
                continue;
            }

            // but some do not, so we want to ensure that our directories exist
            // prior to getting down and funky
            File parent = new File(efile.getParent());
            if (!parent.exists() && !parent.mkdirs()) {
                log.warning("Failed to create jar entry parent", "jar", jar, "parent", parent);
                continue;
            }

            try (BufferedOutputStream fout = new BufferedOutputStream(Files.newOutputStream(efile.toPath()));
                 InputStream jin = jar.getInputStream(entry)) {
                StreamUtil.copy(jin, fout);
            } catch (Exception e) {
                throw new IOException(
                    Log.format("Failure unpacking", "jar", jar, "entry", efile), e);
            }
        }
    }

    /**
     * Copies the given {@code source} file to the given {@code target}.
     */
    public static void copy (File source, File target) throws IOException {
        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(target)) {
            StreamUtil.copy(in, out);
        }
    }

    /**
     * Marks {@code file} as executable, if it exists. Catches and logs any errors that occur.
     */
    public static void makeExecutable (File file) {
        try {
            if (file.exists()) {
                if (!file.setExecutable(true, false)) {
                    log.warning("Failed to mark as executable", "file", file);
                }
            }
        } catch (Exception e) {
            log.warning("Failed to mark as executable", "file", file, "error", e);
        }
    }

    /**
     * Used by {@link #walkTree}.
     */
    public interface Visitor
    {
        void visit (File file);
    }

    /**
     * Walks all files in {@code root}, calling {@code visitor} on each file in the tree.
     */
    public static void walkTree (File root, Visitor visitor)
    {
        File[] children = root.listFiles();
        if (children == null) return;
        Deque<File> stack = new ArrayDeque<>(Arrays.asList(children));
        while (!stack.isEmpty()) {
            File currentFile = stack.pop();
            if (currentFile.exists()) {
                visitor.visit(currentFile);
                File[] currentChildren = currentFile.listFiles();
                if (currentChildren != null) {
                    for (File file : currentChildren) {
                        stack.push(file);
                    }
                }
            }
        }
    }

    /**
     * Returns files as Paths from path {@code basePath}, filtered Glob pattern {@code globPattern}.
     */
    public static Set<Path> getFilePathsByGlob(String basePath, String globPattern) {

        //massaging glob pattern
        final String globPatternUnixSeparator = globPattern.replace("\\", "/");
        final String glob = String.format("glob:%s%s%s",
            basePath.replace("\\", "/"),
            globPatternUnixSeparator.startsWith("/") ? "" : "/",
            globPatternUnixSeparator);

        final PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher(glob);
        final Set<Path> foundFilePaths = new HashSet<>();

        try {
            Files.walkFileTree(Paths.get(basePath), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path path,
                    BasicFileAttributes attrs) {
                    if (pathMatcher.matches(path) && !Files.isDirectory(path)) {
                        foundFilePaths.add(path);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception e) {
            log.warning(String.format("Failed to get file list with path %s with glob %s",
                basePath, globPattern), "error", e);
            return Collections.emptySet();
        }

        return foundFilePaths;
    }
}
