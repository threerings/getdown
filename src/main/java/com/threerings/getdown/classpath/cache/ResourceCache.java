package com.threerings.getdown.classpath.cache;

import java.io.File;
import java.io.IOException;

import com.threerings.getdown.util.FileUtil;

/**
 * Maintains a cache of code resources. The cache allows multiple application instances of different
 * versions to open at the same time.
 */
public class ResourceCache
{
    public ResourceCache (File _cacheDir) throws IOException
    {
        this._cacheDir = _cacheDir;

        createDirectoryIfNecessary(_cacheDir);
    }

    private void createDirectoryIfNecessary (File dir) throws IOException
    {
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("unable to create directory: " + dir.getAbsolutePath());
        }
    }

    /**
     * Caches the given file under it's {@code digest}.
     *
     * @return the cached file
     */
    public File cacheFile (File fileToCache, String digest) throws IOException
    {
        File cacheLocation = new File(_cacheDir, digest.substring(0, 2));
        createDirectoryIfNecessary(cacheLocation);

        File cachedFile = new File(cacheLocation, digest + getFileSuffix(fileToCache));
        File lastAccessedFile = new File(
                cacheLocation, cachedFile.getName() + LAST_ACCESSED_FILE_SUFFIX);

        if (!cachedFile.exists()) {
            createNewFile(cachedFile);
            FileUtil.copy(fileToCache, cachedFile);
        }

        if (lastAccessedFile.exists()) {
            lastAccessedFile.setLastModified(System.currentTimeMillis());
        } else {
            createNewFile(lastAccessedFile);
        }

        return cachedFile;
    }

    private void createNewFile (File fileToCreate) throws IOException
    {
        if (!fileToCreate.exists() && !fileToCreate.createNewFile()) {
            throw new IOException("unable to create new file: " + fileToCreate.getAbsolutePath());
        }
    }

    private String getFileSuffix (File fileToCache) {
        String fileName = fileToCache.getName();
        int index = fileName.lastIndexOf(".");

        return index > -1 ? fileName.substring(index) : "";
    }

    private final File _cacheDir;

    static final String LAST_ACCESSED_FILE_SUFFIX = ".lastAccessed";
}
