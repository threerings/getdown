//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2018 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.data;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;

import com.threerings.getdown.cache.GarbageCollector;
import com.threerings.getdown.cache.ResourceCache;
import com.threerings.getdown.util.FileUtil;
import static com.threerings.getdown.Log.log;

public class PathBuilder
{
    /** Name of directory to store cached code files in. */
    public static final String CODE_CACHE_DIR = ".cache";

    /** Name of directory to store cached native resources in. */
    public static final String NATIVE_CACHE_DIR = ".ncache";

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
        File codeCacheDir = new File(app.getAppDir(), CODE_CACHE_DIR);

        // a negative value of code_cache_retention_days allows to clean up the cache forcefully
        long retainMillis = TimeUnit.DAYS.toMillis(app.getCodeCacheRetentionDays());
        if (retainMillis != 0L) {
            GarbageCollector.collect(codeCacheDir, retainMillis);
        }

        ResourceCache cache = new ResourceCache(codeCacheDir);
        LinkedHashSet<File> classPathEntries = new LinkedHashSet<>();
        for (Resource resource: app.getActiveCodeResources()) {
            String digest = app.getDigest(resource);
            File entry = cache.cacheFile(resource.getFinalTarget(), digest.substring(0, 2), digest);
            classPathEntries.add(entry);
        }

        return new ClassPath(classPathEntries);
    }

    /**
     * Builds a {@link ClassPath} instance by first caching all native jars (indicated by
     * nresource=[native jar]), unpacking them, and referencing the locations of each of the
     * unpacked files. Also performs garbage collection similar to {@link #buildCachedClassPath}
     *
     * @param app                   used to determine native jars and related information.
     * @param addCurrentLibraryPath if true, it adds the locations referenced by
     *                              {@code System.getProperty("java.library.path")} as well.
     * @return a classpath instance if at least one native resource was found and unpacked,
     *         {@code null} if no native resources were used by the application.
     */
    public static ClassPath buildLibsPath (Application app,
                                           boolean addCurrentLibraryPath) throws IOException {
        List<Resource> resources = app.getNativeResources();
        if (resources.isEmpty()) {
            return null;
        }

        LinkedHashSet<File> nativedirs = new LinkedHashSet<>();
        File nativeCacheDir = new File(app.getAppDir(), NATIVE_CACHE_DIR);
        ResourceCache cache = new ResourceCache(nativeCacheDir);

        // negative value forces total garbage collection, 0 avoids garbage collection at all
        long retainMillis = TimeUnit.DAYS.toMillis(app.getCodeCacheRetentionDays());
        if (retainMillis != 0L) {
            GarbageCollector.collectNative(nativeCacheDir, retainMillis);
        }

        for (Resource resource : resources) {
            // Use untruncated cache subdirectory names to avoid overwriting issues when unpacking,
            // in the off chance that two native jars share a directory AND contain files with the
            // same names
            String digest = app.getDigest(resource);
            File cachedFile = cache.cacheFile(resource.getFinalTarget(), digest, digest);
            File cachedParent = cachedFile.getParentFile();
            File unpackedIndicator = new File(cachedParent, cachedFile.getName() + ".unpacked");

            if (!unpackedIndicator.exists()) {
                try {
                    FileUtil.unpackJar(new JarFile(cachedFile), cachedParent, false);
                    unpackedIndicator.createNewFile();
                } catch (IOException ioe) {
                    log.warning("Failed to unpack native jar",
                                "file", cachedFile.getAbsolutePath(), ioe);
                    // Keep going and unpack the other jars...
                }
            }

            nativedirs.add(cachedFile.getParentFile());
        }

        if (addCurrentLibraryPath) {
            for (String path : System.getProperty("java.library.path").split(File.pathSeparator)) {
                nativedirs.add(new File(path));
            }
        }

        return new ClassPath(nativedirs);
    }
}
