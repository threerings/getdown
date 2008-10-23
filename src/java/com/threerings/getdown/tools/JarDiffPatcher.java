//
// $Id$
//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2008 Three Rings Design, Inc.
//
// Redistribution and use in source and binary forms, with or without modification, are permitted
// provided that the following conditions are met:
// 
// 1. Redistributions of source code must retain the above copyright notice, this list of
//    conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright notice, this list of
//    conditions and the following disclaimer in the documentation and/or other materials provided
//    with the distribution.
// 
// THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES,
// INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
// PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT,
// INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
// TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
// LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
// SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
package com.threerings.getdown.tools;

import java.io.IOException;
import java.io.OutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.LineNumberReader;
import java.io.InputStreamReader;

import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;

import java.util.jar.JarOutputStream;
import java.util.jar.JarFile;
import java.util.jar.JarEntry;

import com.threerings.getdown.util.ProgressObserver;

/**
 * Applies a jardiff patch to a jar file.
 */
public class JarDiffPatcher
{
    /** The name of the jardiff control file. */
    public static final String INDEX_NAME = "META-INF/INDEX.JD";

    /** The version header used in the control file. */
    public static final String VERSION_HEADER = "version 1.0";

    /** A jardiff command to remove an entry. */
    public static final String REMOVE_COMMAND = "remove";

    /** A jardiff command to move an entry. */
    public static final String MOVE_COMMAND = "move";

    /**
     * Patches the specified jar file using the supplied patch file and writing
     * the new jar file to the supplied target.
     *
     * @param jarPath the path to the original jar file.
     * @param diffPath the path to the jardiff patch file.
     * @param target the output stream to which we will write the patched jar.
     * @param observer an optional observer to be notified of patching progress.
     *
     * @throws IOException if any problem occurs during patching.
     */
    public void patchJar (String jarPath, String diffPath, OutputStream target,
                          ProgressObserver observer)
        throws IOException
    {
        File oldFile = new File(jarPath);
        File diffFile = new File(diffPath);
        JarOutputStream jos = new JarOutputStream(target);
        JarFile oldJar = new JarFile(oldFile);
        JarFile jarDiff = new JarFile(diffFile);
        Set<String> ignoreSet = new HashSet<String>();

        Map<String, String> renameMap = new HashMap<String, String>();
        determineNameMapping(jarDiff, ignoreSet, renameMap);

        // get all keys in renameMap
        Object[] keys = renameMap.keySet().toArray();

        // Files to implicit move
        Set<String> oldjarNames  = new HashSet<String>();
        Enumeration<JarEntry> oldEntries = oldJar.entries();
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
        Enumeration<JarEntry> entries = jarDiff.entries();
        if (entries != null) {
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
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
        for (int j = 0; j < keys.length; j++) {
            // Apply move <oldName> <newName> command
            String newName = (String)keys[j];
            String oldName = renameMap.get(newName);

            // Get source JarEntry
            JarEntry oldEntry = oldJar.getJarEntry(oldName);
            if (oldEntry == null) {
                String moveCmd = MOVE_COMMAND + oldName + " " + newName;
                throw new IOException("error.badmove: " + moveCmd);
            }

            // Create dest JarEntry
            JarEntry newEntry = new JarEntry(newName);
            newEntry.setTime(oldEntry.getTime());
            newEntry.setSize(oldEntry.getSize());
            newEntry.setCompressedSize(oldEntry.getCompressedSize());
            newEntry.setCrc(oldEntry.getCrc());
            newEntry.setMethod(oldEntry.getMethod());
            newEntry.setExtra(oldEntry.getExtra());
            newEntry.setComment(oldEntry.getComment());

            updateObserver(observer, currentEntry, size);
            currentEntry++;

            writeEntry(jos, newEntry, oldJar.getInputStream(oldEntry));

            // Remove entry from oldjarNames since no implicit move is needed
            boolean wasInOld = oldjarNames.remove(oldName);

            // Update progress counters. If it was in old, we do not need an
            // implicit move, so adjust total size.
            if (wasInOld) {
                size--;
            }
        }

        // implicit move
        Iterator iEntries = oldjarNames.iterator();
        if (iEntries != null) {
            while (iEntries.hasNext()) {
                String name = (String)iEntries.next();
                JarEntry entry = oldJar.getJarEntry(name);
                updateObserver(observer, currentEntry, size);
                currentEntry++;
                writeEntry(jos, entry, oldJar);
            }
        }
        updateObserver(observer, currentEntry, size);

        jos.finish();
    }

    protected void updateObserver (ProgressObserver observer,
                                   double currentSize, double size)
    {
	if (observer != null) {
	    observer.progress((int)(100*currentSize/size));
	}
    }

    protected void determineNameMapping (
        JarFile jarDiff, Set<String> ignoreSet, Map<String, String> renameMap)
        throws IOException
    {
        InputStream is = jarDiff.getInputStream(jarDiff.getEntry(INDEX_NAME));
        if (is == null) {
            throw new IOException("error.noindex");
        }

        LineNumberReader indexReader =
            new LineNumberReader(new InputStreamReader(is, "UTF-8"));
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
        ArrayList<String> sub = new ArrayList<String>();

        while (index < length) {
            while (index < length && Character.isWhitespace
                   (path.charAt(index))) {
                index++;
            }
            if (index < length) {
                int start = index;
                int last = start;
                String subString = null;

                while (index < length) {
                    char aChar = path.charAt(index);
                    if (aChar == '\\' && (index + 1) < length &&
                        path.charAt(index + 1) == ' ') {

                        if (subString == null) {
                            subString = path.substring(last, index);
                        } else {
                            subString += path.substring(last, index);
                        }
                        last = ++index;
                    } else if (Character.isWhitespace(aChar)) {
                        break;
                    }
                    index++;
                }
                if (last != index) {
                    if (subString == null) {
                        subString = path.substring(last, index);
                    } else {
                        subString += path.substring(last, index);
                    }
                }
                sub.add(subString);
            }
        }
        return sub;
    }

    protected void writeEntry (
        JarOutputStream jos, JarEntry entry, JarFile file)
        throws IOException
    {
        writeEntry(jos, entry, file.getInputStream(entry));
    }

    protected void writeEntry (
        JarOutputStream jos, JarEntry entry, InputStream data)
        throws IOException
    {
        jos.putNextEntry(new JarEntry(entry.getName()));

        // Read the entry
        int size = data.read(newBytes);
        while (size != -1) {
            jos.write(newBytes, 0, size);
            size = data.read(newBytes);
        }
        data.close();
    }

    protected static final int DEFAULT_READ_SIZE = 2048;

    protected static byte[] newBytes = new byte[DEFAULT_READ_SIZE];
    protected static byte[] oldBytes = new byte[DEFAULT_READ_SIZE];
}
