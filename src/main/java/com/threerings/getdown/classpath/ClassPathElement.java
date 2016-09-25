package com.threerings.getdown.classpath;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Represents an element of an applications class path.
 */
public class ClassPathElement
{
    public ClassPathElement (File _file)
    {
        this._file = _file;
    }

    /**
     * Returns the code resource.
     */
    public File getFile ()
    {
        return _file;
    }


    /**
     * Returns the elements absolute path on the file system.
     */
    public String getAbsolutePath ()
    {
        return _file.getAbsolutePath();
    }

    /**
     * Returns the file URL of this class path element.
     */
    public URL getURL ()
    {
        try {
            return _file.toURI().toURL();
        } catch (MalformedURLException e) {
            throw new IllegalStateException("URL of file is illegal: " + getAbsolutePath(), e);
        }
    }

    private final File _file;
}
