package com.threerings.getdown.classpath.cache;

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
                        cachedFile.delete();
                    } else {
                        lastAccessedFile.delete();
                    }
                } else if (shouldDelete(lastAccessedFile, retentionPeriodMillis)) {
                    lastAccessedFile.delete();
                    cachedFile.delete();
                }

                File folder = file.getParentFile();
                if (folder.list().length == 0) {
                    folder.delete();
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
