//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2016 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.classpath;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.concurrent.TimeUnit;

import com.threerings.getdown.classpath.cache.GarbageCollector;
import com.threerings.getdown.classpath.cache.ResourceCache;
import com.threerings.getdown.data.Application;
import com.threerings.getdown.data.Resource;

public class ClassPaths
{
    static final String CACHE_DIR = ".cache";

    /**
     * Builds either a default or cached classpath based on {@code app}'s configuration.
     */
    public static ClassPath buildClassPath (Application app) throws IOException
    {
        return app.useCodeCache() ? buildCachedClassPath(app) : buildDefaultClassPath(app);
    }

    /**
     * Builds a {@link ClassPath} instance for {@code app} using the code resources in place in
     * the app directory.
     */
    public static ClassPath buildDefaultClassPath (Application app)
    {
        LinkedHashSet<File> classPathEntries = new LinkedHashSet<File>();
        for (Resource resource: app.getActiveCodeResources()) {
            classPathEntries.add(resource.getFinalTarget());
        }
        return new ClassPath(classPathEntries);
    }

    /**
     * Builds a {@link ClassPath} instance for {@code app} by first copying the code resources into
     * a cache directory and then referencing them from there. This avoids problems with
     * overwriting in-use classpath elements when the application is later updated. This also
     * "garbage collects" expired caches if necessary.
     */
    public static ClassPath buildCachedClassPath (Application app) throws IOException
    {
        File cacheDir = new File(app.getAppDir(), CACHE_DIR);
        // a negative value of code_cache_retention_days allows to clean up the cache forcefully
        if (app.getCodeCacheRetentionDays() <= 0) {
            runGarbageCollection(app, cacheDir);
        }

        ResourceCache cache = new ResourceCache(cacheDir);
        LinkedHashSet<File> classPathEntries = new LinkedHashSet<File>();
        for (Resource resource: app.getActiveCodeResources()) {
            File entry = cache.cacheFile(resource.getFinalTarget(), app.getDigest(resource));
            classPathEntries.add(entry);
        }

        if (app.getCodeCacheRetentionDays() > 0) {
            runGarbageCollection(app, cacheDir);
        }

        return new ClassPath(classPathEntries);
    }

    private static void runGarbageCollection (Application app, File cacheDir)
    {
        long retainMillis = TimeUnit.DAYS.toMillis(app.getCodeCacheRetentionDays());
        GarbageCollector.collect(cacheDir, retainMillis);
    }
}
