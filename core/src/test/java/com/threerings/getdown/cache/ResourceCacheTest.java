//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2018 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.cache;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import static org.junit.Assert.*;

/**
 * Asserts the correct functionality of the {@link ResourceCache}.
 */
@RunWith(Parameterized.class)
public class ResourceCacheTest
{
    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {{ ".jar" }, { ".zip" }});
    }

    @Parameterized.Parameter
    public String extension;

    @Before public void setupCache () throws IOException {
        _fileToCache = _folder.newFile("filetocache" + extension);
        _cache = new ResourceCache(_folder.newFolder(".cache"));
    }

    @Test public void shouldCacheFile () throws IOException
    {
        assertEquals("abc123" + extension, cacheFile().getName());
    }

    private File cacheFile() throws IOException
    {
        return _cache.cacheFile(_fileToCache, "abc123", "abc123");
    }

    @Test public void shouldTrackFileUsage () throws IOException
    {
        String name = "abc123" + extension + ResourceCache.LAST_ACCESSED_FILE_SUFFIX;
        File lastAccessedFile = new File(cacheFile().getParentFile(), name);
        assertTrue(lastAccessedFile.exists());
    }

    @Test public void shouldNotCacheTheSameFile () throws Exception
    {
        File cachedFile = cacheFile();
        cachedFile.setLastModified(YESTERDAY);
        long expectedLastModified = cachedFile.lastModified();
        // caching it another time
        File sameCachedFile = cacheFile();
        assertEquals(expectedLastModified, sameCachedFile.lastModified());
    }

    @Test public void shouldRememberWhenFileWasRequested () throws Exception
    {
        File cachedFile = cacheFile();
        String name = cachedFile.getName() + ResourceCache.LAST_ACCESSED_FILE_SUFFIX;
        File lastAccessedFile = new File(cachedFile.getParentFile(), name);
        lastAccessedFile.setLastModified(YESTERDAY);
        long lastAccessed = lastAccessedFile.lastModified();
        // caching it another time
        cacheFile();
        assertTrue(lastAccessedFile.lastModified() > lastAccessed);
    }

    @Rule public final TemporaryFolder _folder = new TemporaryFolder();

    private File _fileToCache;
    private ResourceCache _cache;

    private static final long YESTERDAY = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1);
}
