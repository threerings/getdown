//
// $Id: Resource.java,v 1.2 2004/07/02 15:22:49 mdb Exp $

package com.threerings.getdown.data;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import java.net.MalformedURLException;
import java.net.URL;

import java.security.MessageDigest;

import com.samskivert.util.StringUtil;

/**
 * Models a single file resource used by an {@link Application}.
 */
public class Resource
{
    /**
     * Creates a resource with the supplied remote URL and local path.
     */
    public Resource (String path, URL remote, File local)
    {
        _path = path;
        _remote = remote;
        _local = local;
    }

    /**
     * Returns the path associated with this resource.
     */
    public String getPath ()
    {
        return _path;
    }

    /**
     * Computes the MD5 hash of this resource's underlying file.
     * <em>Note:</m> This is both CPU and I/O intensive.
     */
    public String computeDigest (MessageDigest md)
        throws IOException
    {
        md.reset();
        byte[] buffer = new byte[DIGEST_BUFFER_SIZE];
        int read;
        FileInputStream fin = new FileInputStream(_local);
        while ((read = fin.read(buffer)) != -1) {
            md.update(buffer, 0, read);
        }
        return StringUtil.hexlate(md.digest());
    }

    /**
     * Returns a string representation of this instance.
     */
    public String toString ()
    {
        // return _path;
        // return _netloc.toString();
        return _local.getPath();
    }

    protected String _path;
    protected URL _remote;
    protected File _local;

    protected final static int DIGEST_BUFFER_SIZE = 5 * 1025;
}
