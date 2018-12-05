package com.threerings.getdown.classpath;

import com.threerings.getdown.classpath.cache.GarbageCollector;
import com.threerings.getdown.classpath.cache.ResourceCache;
import com.threerings.getdown.data.Application;
import com.threerings.getdown.data.Resource;
import com.threerings.getdown.util.FileUtil;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;
import static com.threerings.getdown.Log.log;

/**
 * Similar to {@link ClassPath}, except used to represent directories containing native libraries instead.
 */
public class NativeLibPath extends ClassPath {

    public NativeLibPath(LinkedHashSet<File> entries) {
        super(entries);
    }

    /**
     * Builds a {@link NativeLibPath} instance by first caching all native jars (indicated by
     * nresource=[native jar]), unpacking them, and referencing the locations of each of the unpacked files.
     * Also performs garbage collection similar to {@link ClassPaths#buildCachedClassPath}
     *
     * @param app                   used to determine native jars and related information
     * @param addCurrentLibraryPath if true, it adds the locations referenced by
     *                              {@code System.getProperty("java.library.path")} as well
     */
    public static NativeLibPath buildLibsPath(Application app, boolean addCurrentLibraryPath) throws IOException {
        List<Resource> resources = app.getNativeJars();
        String[] curPaths = System.getProperty("java.library.path").split(File.pathSeparator);
        LinkedHashSet<File> nativedirs = new LinkedHashSet<>();

        File nativeCacheDir = new File(app.getAppDir(), Application.CACHE_DIR + "/native");
        ResourceCache cache = new ResourceCache(nativeCacheDir);

        // negative value forces total garbage collection, 0 avoids garbage collection at all
        if (app.getCodeCacheRetentionDays() != 0) {
            runGarbageCollection(app, nativeCacheDir);
        }

        for (Resource resource : resources) {
            // Use untruncated directory names because in the off chance that two native jars share a directory AND
            // contain files with the same names, we'll get overwriting issues when unpacking
            File cachedFile = cache.cacheFile(resource.getFinalTarget(), app.getDigest(resource), true);

            if (!getUnpackedIndicator(cachedFile).exists()) {
                try {
                    FileUtil.unpackJar(new JarFile(cachedFile), cachedFile.getParentFile(), false);
                    getUnpackedIndicator(cachedFile).createNewFile();
                } catch (IOException ioe) {
                    log.warning("Failed to unpack native jar", "File", cachedFile.getAbsolutePath(), ioe);
                    // Keep going and unpack the other jars...
                }
            }

            nativedirs.add(cachedFile.getParentFile());
        }

        if (addCurrentLibraryPath) {
            for (String path : curPaths) {
                nativedirs.add(new File(path));
            }
        }

        return new NativeLibPath(nativedirs);
    }

    private static void runGarbageCollection(Application app, File nativeCacheDir) {
        long retainMillis = TimeUnit.DAYS.toMillis(app.getCodeCacheRetentionDays());
        GarbageCollector.collectNative(nativeCacheDir, retainMillis);
    }

    private static File getUnpackedIndicator(File cachedFile) {
        return new File(cachedFile.getParent(), cachedFile.getName() + ".unpacked");
    }
}
