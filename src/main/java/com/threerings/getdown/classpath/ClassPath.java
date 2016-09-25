package com.threerings.getdown.classpath;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Represents the class path and it's elements of the application to be launched. The class path can
 * either be represented as an {@link #asArgumentString() argument string} for the java command line
 * or as an {@link #asUrls() array of URLs} to be used by a {@link URLClassLoader}.
 */
public class ClassPath
{
    public ClassPath (LinkedHashSet<ClassPathElement> classPathEntries)
    {
        this._classPathEntries = Collections.unmodifiableSet(classPathEntries);
    }

    /**
     * Returns the class path as an java command line argument string, e.g
     *
     * <pre>
     *   /path/to/a.jar:/path/to/b.jar
     * </pre>
     */
    public String asArgumentString ()
    {
        StringBuilder builder = new StringBuilder();
        String delimiter = "";

        for (ClassPathElement entry: _classPathEntries)
        {
            builder
                .append(delimiter)
                .append(entry.getAbsolutePath());

            delimiter = File.pathSeparator;
        }

        return builder.toString();
    }

    /**
     * Returns the class path entries as an array of URLs to be used for example by an
     * {@link URLClassLoader}.
     */
    public URL[] asUrls ()
    {
        URL[] urls = new URL[_classPathEntries.size()];

        int i = 0;

        for (ClassPathElement entry : _classPathEntries) {
            urls[i++] = entry.getURL();
        }

        return urls;
    }

    public Set<ClassPathElement> getClassPathEntries ()
    {
        return _classPathEntries;
    }

    private final Set<ClassPathElement> _classPathEntries;
}
