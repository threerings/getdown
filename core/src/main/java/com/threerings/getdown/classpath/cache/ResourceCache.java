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
     * Caches the given file under it's {@code digest}. Same as calling {@link #cacheFile(File, String, boolean)}
     * with {@code false}
     * @return the cached file
     */
    public File cacheFile (File fileToCache, String digest) throws IOException
    {
        return cacheFile(fileToCache, digest, false);
    }

    /**
     * Caches the given file under it's {@code digest}.
     * @param fileToCache file to cache
     * @param digest used to determine the name of the directory to store file in
     * @param useFullHashName if true, the name of the cache directory will not be truncated.
     *                        This is useful if you need to avoid the possibility of two files
     *                        sharing a directory.
     * @return the cached file
     */
    public File cacheFile (File fileToCache, String digest, boolean useFullHashName) throws IOException
    {
        File cacheLocation;

        if (useFullHashName) {
            cacheLocation = new File(_cacheDir, digest);
        } else {
            cacheLocation = new File(_cacheDir, digest.substring(0, 2));

        }
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
