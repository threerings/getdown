//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2018 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.data;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Represents the class path and it's elements of the application to be launched. The class path
 * can either be represented as an {@link #asArgumentString() argument string} for the java command
 * line or as an {@link #asUrls() array of URLs} to be used by a {@link URLClassLoader}.
 */
public class ClassPath
{
    public ClassPath (LinkedHashSet<File> classPathEntries)
    {
        _classPathEntries = Collections.unmodifiableSet(classPathEntries);
    }

    /**
     * Returns the class path as an java command line argument string, e.g.
     *
     * <pre>
     *   /path/to/a.jar:/path/to/b.jar
     * </pre>
     */
    public String asArgumentString ()
    {
        StringBuilder builder = new StringBuilder();
        String delimiter = "";
        for (File entry: _classPathEntries) {
            builder.append(delimiter).append(entry.getAbsolutePath());
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
        for (File entry : _classPathEntries) {
            urls[i++] = getURL(entry);
        }
        return urls;
    }

    public Set<File> getClassPathEntries ()
    {
        return _classPathEntries;
    }


    private static URL getURL (File file)
    {
        try {
            return file.toURI().toURL();
        } catch (MalformedURLException e) {
            throw new IllegalStateException("URL of file is illegal: " + file.getAbsolutePath(), e);
        }
    }

    private final Set<File> _classPathEntries;
}
