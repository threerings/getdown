package com.threerings.getdown.classpath.cache;

import java.io.File;
import java.io.FileFilter;

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
     * lastmodified file, and deletes the entire directory accordingly.
     */
    public static void collectNative (File cacheDir, final long retentionPeriodMillis) {
        FileUtil.walkDirectChildren(cacheDir, new FileUtil.Visitor() {
            @Override
            public void visit(File file) {
                if (file.isDirectory()) {
                    // Get all the native jars in the directory (there should only be one though)
                    File[] nativejars = file.listFiles(new FileFilter() {
                        @Override
                        public boolean accept(File pathname) {
                            return pathname.getAbsolutePath().endsWith(".jar");
                        }
                    });
                    for (File nativejar : nativejars) {
                        if (nativejar==null || !nativejar.exists()) {
                            continue;
                        }

                        File cachedFile = getCachedFile(nativejar);
                        File lastAccessedFile = getLastAccessedFile(nativejar);

                        if (!cachedFile.exists() || !lastAccessedFile.exists() || shouldDelete(lastAccessedFile, retentionPeriodMillis)) {
                           FileUtil.deleteDirHarder(file);
                        }
                    }
                } else {
                    // @TODO There shouldn't be any loose files in native/ but if there are then what? Delete them?
                    //  file.delete();
                }
            }
        });
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
