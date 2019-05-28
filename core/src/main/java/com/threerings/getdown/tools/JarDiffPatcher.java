//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2018 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.tools;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import com.threerings.getdown.util.ProgressObserver;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Applies a jardiff patch to a jar/zip file.
 */
public class JarDiffPatcher implements JarDiffCodes
{
    /**
     * Patches the specified jar file using the supplied patch file and writing the new jar file to
     * the supplied target.
     *
     * @param jarPath the path to the original jar file.
     * @param diffPath the path to the jardiff patch file.
     * @param target the output stream to which we will write the patched jar.
     * @param observer an optional observer to be notified of patching progress.
     *
     * @throws IOException if any problem occurs during patching.
     */
    public void patchJar (String jarPath, String diffPath, File target, ProgressObserver observer)
        throws IOException
    {
        File oldFile = new File(jarPath), diffFile = new File(diffPath);
        try (ZipFile oldJar = new ZipFile(oldFile);
             ZipFile jarDiff = new ZipFile(diffFile);
             ZipOutputStream jos = makeOutputStream(oldFile, target)) {
            Set<String> ignoreSet = new HashSet<>();
            Map<String, String> renameMap = new HashMap<>();
            determineNameMapping(jarDiff, ignoreSet, renameMap);

            // get all keys in renameMap
            String[] keys = renameMap.keySet().toArray(new String[renameMap.size()]);

            // Files to implicit move
            Set<String> oldjarNames  = new HashSet<>();
            Enumeration<? extends ZipEntry> oldEntries = oldJar.entries();
            if (oldEntries != null) {
                while  (oldEntries.hasMoreElements()) {
                    oldjarNames.add(oldEntries.nextElement().getName());
                }
            }

            // size depends on the three parameters below, which is basically the
            // counter for each loop that do the actual writes to the output file
            // since oldjarNames.size() changes in the first two loop below, we
            // need to adjust the size accordingly also when oldjarNames.size()
            // changes
            double size = oldjarNames.size() + keys.length + jarDiff.size();
            double currentEntry = 0;

            // Handle all remove commands
            oldjarNames.removeAll(ignoreSet);
            size -= ignoreSet.size();

            // Add content from JARDiff
            Enumeration<? extends ZipEntry> entries = jarDiff.entries();
            if (entries != null) {
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (!INDEX_NAME.equals(entry.getName())) {
                        updateObserver(observer, currentEntry, size);
                        currentEntry++;
                        writeEntry(jos, entry, jarDiff);

                        // Remove entry from oldjarNames since no implicit move is
                        // needed
                        boolean wasInOld = oldjarNames.remove(entry.getName());

                        // Update progress counters. If it was in old, we do not
                        // need an implicit move, so adjust total size.
                        if (wasInOld) {
                            size--;
                        }

                    } else {
                        // no write is done, decrement size
                        size--;
                    }
                }
            }

            // go through the renameMap and apply move for each entry
            for (String newName : keys) {
                // Apply move <oldName> <newName> command
                String oldName = renameMap.get(newName);

                // Get source ZipEntry
                ZipEntry oldEntry = oldJar.getEntry(oldName);
                if (oldEntry == null) {
                    String moveCmd = MOVE_COMMAND + oldName + " " + newName;
                    throw new IOException("error.badmove: " + moveCmd);
                }

                // Create dest ZipEntry
                ZipEntry newEntry = new ZipEntry(newName);
                newEntry.setTime(oldEntry.getTime());
                newEntry.setSize(oldEntry.getSize());
                newEntry.setCompressedSize(oldEntry.getCompressedSize());
                newEntry.setCrc(oldEntry.getCrc());
                newEntry.setMethod(oldEntry.getMethod());
                newEntry.setExtra(oldEntry.getExtra());
                newEntry.setComment(oldEntry.getComment());

                updateObserver(observer, currentEntry, size);
                currentEntry++;

                try (InputStream data = oldJar.getInputStream(oldEntry)) {
                    writeEntry(jos, newEntry, data);
                }

                // Remove entry from oldjarNames since no implicit move is needed
                boolean wasInOld = oldjarNames.remove(oldName);

                // Update progress counters. If it was in old, we do not need an
                // implicit move, so adjust total size.
                if (wasInOld) {
                    size--;
                }
            }

            // implicit move
            for (String name : oldjarNames) {
                ZipEntry entry = oldJar.getEntry(name);
                if (entry == null) {
                    // names originally retrieved from the archive, so this should never happen
                    throw new AssertionError("Archive entry not found: " + name);
                }
                updateObserver(observer, currentEntry, size);
                currentEntry++;
                writeEntry(jos, entry, oldJar);
            }
            updateObserver(observer, currentEntry, size);
        }
    }

    protected void updateObserver (ProgressObserver observer, double currentSize, double size)
    {
        if (observer != null) {
            observer.progress((int)(100*currentSize/size));
        }
    }

    protected void determineNameMapping (
        ZipFile jarDiff, Set<String> ignoreSet, Map<String, String> renameMap)
        throws IOException
    {
        InputStream is = jarDiff.getInputStream(jarDiff.getEntry(INDEX_NAME));
        if (is == null) {
            throw new IOException("error.noindex");
        }

        LineNumberReader indexReader =
            new LineNumberReader(new InputStreamReader(is, UTF_8));
        String line = indexReader.readLine();
        if (line == null || !line.equals(VERSION_HEADER)) {
            throw new IOException("jardiff.error.badheader: " + line);
        }

        while ((line = indexReader.readLine()) != null) {
            if (line.startsWith(REMOVE_COMMAND)) {
                List<String> sub = getSubpaths(
                    line.substring(REMOVE_COMMAND.length()));

                if (sub.size() != 1) {
                    throw new IOException("error.badremove: " + line);
                }
                ignoreSet.add(sub.get(0));

            } else if (line.startsWith(MOVE_COMMAND)) {
                List<String> sub = getSubpaths(
                    line.substring(MOVE_COMMAND.length()));
                if (sub.size() != 2) {
                    throw new IOException("error.badmove: " + line);
                }

                // target of move should be the key
                if (renameMap.put(sub.get(1), sub.get(0)) != null) {
                    // invalid move - should not move to same target twice
                    throw new IOException("error.badmove: " + line);
                }

            } else if (line.length() > 0) {
                throw new IOException("error.badcommand: " + line);
            }
        }
    }

    protected List<String> getSubpaths (String path)
    {
        int index = 0;
        int length = path.length();
        List<String> sub = new ArrayList<>();

        while (index < length) {
            while (index < length && Character.isWhitespace
                   (path.charAt(index))) {
                index++;
            }
            if (index < length) {
                int last = index;
                StringBuilder subString = null;

                while (index < length) {
                    char aChar = path.charAt(index);
                    if (aChar == '\\' && (index + 1) < length &&
                        path.charAt(index + 1) == ' ') {

                        if (subString == null) {
                            subString = new StringBuilder(path.substring(last, index));
                        } else {
                            subString.append(path, last, index);
                        }
                        last = ++index;
                    } else if (Character.isWhitespace(aChar)) {
                        break;
                    }
                    index++;
                }
                if (last != index) {
                    if (subString == null) {
                        subString = new StringBuilder(path.substring(last, index));
                    } else {
                        subString.append(path, last, index);
                    }
                }
                if (subString != null) {
                    sub.add(subString.toString());
                }
            }
        }
        return sub;
    }

    protected void writeEntry (ZipOutputStream jos, ZipEntry entry, ZipFile file)
        throws IOException
    {
        try (InputStream data = file.getInputStream(entry)) {
            writeEntry(jos, entry, data);
        }
    }

    protected void writeEntry (ZipOutputStream jos, ZipEntry entry, InputStream data)
        throws IOException
    {
        jos.putNextEntry(new ZipEntry(entry.getName()));

        // Read the entry
        int size = data.read(newBytes);
        while (size != -1) {
            jos.write(newBytes, 0, size);
            size = data.read(newBytes);
        }
    }

    protected static ZipOutputStream makeOutputStream (File source, File target)
        throws IOException
    {
        FileOutputStream out = new FileOutputStream(target);
        if (source.getName().endsWith(".jar")) return new JarOutputStream(out);
        else if (source.getName().endsWith(".zip")) return new ZipOutputStream(out);
        else throw new AssertionError("Unsupported source file '" + source + "'. Not a .jar or .zip?");
    }

    protected static final int DEFAULT_READ_SIZE = 2048;

    protected static byte[] newBytes = new byte[DEFAULT_READ_SIZE];
    protected static byte[] oldBytes = new byte[DEFAULT_READ_SIZE];
}
