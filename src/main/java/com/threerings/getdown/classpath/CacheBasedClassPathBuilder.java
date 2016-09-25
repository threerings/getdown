package com.threerings.getdown.classpath;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.concurrent.TimeUnit;

import com.threerings.getdown.classpath.cache.GarbageCollector;
import com.threerings.getdown.classpath.cache.ResourceCache;
import com.threerings.getdown.data.Application;
import com.threerings.getdown.data.Resource;
import com.threerings.getdown.util.file.FileWalker;

/**
 * Instead of building the class path from the application directory, this class path builder puts
 * the files that make up the class path into a cache and assembles the class path from the cache
 * directory.
 */
public class CacheBasedClassPathBuilder extends ClassPathBuilderBase
{
    static final String CACHE_DIR = ".cache";

    public CacheBasedClassPathBuilder(Application application)
    {
        super(application);
    }

    @Override
    public ClassPath buildClassPath () throws IOException
    {
        File cacheDir = new File(_application.getAppdir(), CACHE_DIR);

        // a negative value of code_cache_retention_days allows to clean up the cache forcefully
        if (_application.getCodeCacheRetentionDays() <= 0) {
            runGarbageCollection(cacheDir);
        }

        ResourceCache cache = new ResourceCache(cacheDir);

        LinkedHashSet<ClassPathElement> classPathEntries = new LinkedHashSet<ClassPathElement>();

        for (Resource resource: _application.getActiveCodeResources()) {
            classPathEntries.add(
                new ClassPathElement(
                    cache.cacheFile(resource.getFinalTarget(), _application.getDigest(resource))));
        }

        if (_application.getCodeCacheRetentionDays() > 0) {
            runGarbageCollection(cacheDir);
        }

        return new ClassPath(classPathEntries);
    }

    private void runGarbageCollection(File cacheDir) {
        GarbageCollector gc = new GarbageCollector(
                new FileWalker(cacheDir), _application.getCodeCacheRetentionDays(), TimeUnit.DAYS);

        gc.collectGarbage();
    }
}
