//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2018 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.cache;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/**
 * Validates that cache garbage is collected and deleted correctly.
 */
public class GarbageCollectorTest
{
    @Before public void setupFiles () throws IOException
    {
        _cachedFile = _folder.newFile("abc123.jar");
        _lastAccessedFile = _folder.newFile("abc123.jar" + ResourceCache.LAST_ACCESSED_FILE_SUFFIX);
    }

    @Test public void shouldDeleteCacheEntryIfRetentionPeriodIsReached ()
    {
        gcNow();
        assertFalse(_cachedFile.exists());
        assertFalse(_lastAccessedFile.exists());
    }

    @Test public void shouldDeleteCacheFolderIfFolderIsEmpty ()
    {
        gcNow();
        assertFalse(_folder.getRoot().exists());
    }

    private void gcNow() {
        GarbageCollector.collect(_folder.getRoot(), -1);
    }

    @Test public void shouldKeepFilesInCacheIfRententionPeriodIsNotReached ()
    {
        GarbageCollector.collect(_folder.getRoot(), TimeUnit.DAYS.toMillis(1));
        assertTrue(_cachedFile.exists());
        assertTrue(_lastAccessedFile.exists());
    }

    @Test public void shouldDeleteCachedFileIfLastAccessedFileIsMissing ()
    {
        assumeTrue(_lastAccessedFile.delete());
        gcNow();
        assertFalse(_cachedFile.exists());
    }

    @Test public void shouldDeleteLastAccessedFileIfCachedFileIsMissing ()
    {
        assumeTrue(_cachedFile.delete());
        gcNow();
        assertFalse(_lastAccessedFile.exists());
    }

    @Rule public TemporaryFolder _folder = new TemporaryFolder();

    private File _cachedFile;
    private File _lastAccessedFile;
}
