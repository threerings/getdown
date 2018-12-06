//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2018 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.cache;

import java.io.File;
import com.threerings.getdown.util.FileUtil;

/**
 * Collects elements in the {@link ResourceCache cache} which became unused and deletes them
 * afterwards.
 */
public class GarbageCollector
{
    /**
     * Collect and delete the garbage in the cache.
     */
    public static void collect (File cacheDir, final long retentionPeriodMillis)
    {
        FileUtil.walkTree(cacheDir, new FileUtil.Visitor() {
            @Override public void visit (File file) {
                File cachedFile = getCachedFile(file);
                File lastAccessedFile = getLastAccessedFile(file);
                if (!cachedFile.exists() || !lastAccessedFile.exists()) {
                    if (cachedFile.exists()) {
                        FileUtil.deleteHarder(cachedFile);
                    } else {
                        FileUtil.deleteHarder(lastAccessedFile);
                    }
                } else if (shouldDelete(lastAccessedFile, retentionPeriodMillis)) {
                    FileUtil.deleteHarder(lastAccessedFile);
                    FileUtil.deleteHarder(cachedFile);
                }

                File folder = file.getParentFile();
                if (folder != null) {
                    String[] children = folder.list();
                    if (children != null && children.length == 0) {
                        FileUtil.deleteHarder(folder);
                    }
                }
            }
        });
    }

    /**
     * Collect and delete garbage in the native cache. It tries to find a jar file with a matching
     * last modified file, and deletes the entire directory accordingly.
     */
    public static void collectNative (File cacheDir, final long retentionPeriodMillis)
    {
        File[] subdirs = cacheDir.listFiles();
        if (subdirs != null) {
            for (File dir : subdirs) {
                if (dir.isDirectory()) {
                    // Get all the native jars in the directory (there should only be one)
                    for (File file : dir.listFiles()) {
                        if (!file.getName().endsWith(".jar")) {
                            continue;
                        }
                        File cachedFile = getCachedFile(file);
                        File lastAccessedFile = getLastAccessedFile(file);
                        if (!cachedFile.exists() || !lastAccessedFile.exists() ||
                            shouldDelete(lastAccessedFile, retentionPeriodMillis)) {
                            FileUtil.deleteDirHarder(dir);
                        }
                    }
                } else {
                    // @TODO There shouldn't be any loose files in native/ but if there are then
                    // what? Delete them? file.delete();
                }
            }
        }
    }

    private static boolean shouldDelete (File lastAccessedFile, long retentionMillis)
    {
        return System.currentTimeMillis() - lastAccessedFile.lastModified() > retentionMillis;
    }

    private static File getLastAccessedFile (File file)
    {
        return isLastAccessedFile(file) ? file : new File(
            file.getParentFile(), file.getName() + ResourceCache.LAST_ACCESSED_FILE_SUFFIX);
    }

    private static boolean isLastAccessedFile (File file)
    {
        return file.getName().endsWith(ResourceCache.LAST_ACCESSED_FILE_SUFFIX);
    }

    private static File getCachedFile (File file)
    {
        return !isLastAccessedFile(file) ? file : new File(
            file.getParentFile(), file.getName().substring(0, file.getName().lastIndexOf(".")));
    }
}
