package com.threerings.getdown.classpath.cache;

import java.io.File;
import java.util.concurrent.TimeUnit;

import com.threerings.getdown.util.file.FileVisitor;
import com.threerings.getdown.util.file.FileWalker;

/**
 * Collects elements in the {@link ResourceCache cache} which became unused and deletes them
 * afterwards.
 */
public class GarbageCollector
{
    public GarbageCollector (
            FileWalker _fileWalker, int retentionPeriod, TimeUnit retentionPeriodTimeUnit)
    {
        this._fileWalker = _fileWalker;
        this._retentionMillis = retentionPeriodTimeUnit.toMillis(retentionPeriod);
    }

    /**
     * Collect and delete the garbage in the cache.
     */
    public void collectGarbage ()
    {
        _fileWalker.walkTree(new FileVisitor() {
            @Override
            public void visit(File file)
            {
                collect(file);
            }
        });
    }

    private void collect (File file) {
        File cachedFile = getCachedFile(file);
        File lastAccessedFile = getLastAccessedFile(file);

        if (!cachedFile.exists() || !lastAccessedFile.exists()) {
            if (cachedFile.exists()) {
                cachedFile.delete();
            } else {
                lastAccessedFile.delete();
            }
        } else if (shouldDelete(lastAccessedFile)) {
            lastAccessedFile.delete();
            cachedFile.delete();
        }

        File folder = file.getParentFile();

        if (folder.list().length == 0) {
            folder.delete();
        }
    }

    private boolean shouldDelete(File lastAccessedFile) {
        return System.currentTimeMillis() - lastAccessedFile.lastModified() > _retentionMillis;
    }

    private File getLastAccessedFile (File file)
    {
        if (isLastAccessedFile(file)) {
            return file;
        }

        return new File(
                file.getParentFile(),
                file.getName() + ResourceCache.LAST_ACCESSED_FILE_SUFFIX);
    }

    private boolean isLastAccessedFile (File file)
    {
        return file.getName().endsWith(ResourceCache.LAST_ACCESSED_FILE_SUFFIX);
    }

    private File getCachedFile (File file)
    {
        if (!isLastAccessedFile(file)) {
            return file;
        }

        return new File(
                file.getParentFile(),
                file.getName().substring(0, file.getName().lastIndexOf(".")));
    }

    private final FileWalker _fileWalker;
    private final long _retentionMillis;
}
