package com.threerings.getdown.classpath.cache;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Asserts the correct functionality of the {@link ResourceCache}.
 */
public class ResourceCacheTest
{
    @Before
    public void setupCache () throws IOException {
        _fileToCache = _folder.newFile("filetocache.jar");
        _cache = new ResourceCache(_folder.newFolder(".cache"));
    }

    @Test
    public void shouldCacheFile () throws IOException
    {
        assertEquals("abc123.jar", cacheFile().getName());
    }

    private File cacheFile() throws IOException
    {
        return _cache.cacheFile(_fileToCache, "abc123");
    }

    @Test
    public void shouldTrackFileUsage () throws IOException
    {
        File lastAccessedFile = new File(
                cacheFile().getParentFile(),
                "abc123.jar" + ResourceCache.LAST_ACCESSED_FILE_SUFFIX);

        assertTrue(lastAccessedFile.exists());
    }

    @Test
    public void shouldNotCacheTheSameFile () throws Exception
    {
        File cachedFile = cacheFile();

        TimeUnit.MILLISECONDS.sleep(500);

        // caching it another time
        File sameCachedFile = cacheFile();

        assertEquals(cachedFile.lastModified(), sameCachedFile.lastModified());
    }

    @Test
    public void shouldRememberWhenFileWasRequested () throws Exception
    {
        cacheFile();

        TimeUnit.MILLISECONDS.sleep(500);

        // caching it another time
        File sameCachedFile = cacheFile();
        File lastAccessed = new File(
                sameCachedFile.getParentFile(),
                sameCachedFile.getName() + ResourceCache.LAST_ACCESSED_FILE_SUFFIX);

        assertTrue(lastAccessed.lastModified() > sameCachedFile.lastModified());
    }

    @Rule
    public TemporaryFolder _folder = new TemporaryFolder();

    private File _fileToCache;
    private ResourceCache _cache;
}
