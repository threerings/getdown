//
// $Id: Resource.java,v 1.4 2004/07/06 05:13:36 mdb Exp $

package com.threerings.getdown.data;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;

import com.samskivert.util.StringUtil;

import com.threerings.getdown.Log;

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
        _marker = new File(_local.getPath() + "v");
    }

    /**
     * Returns the path associated with this resource.
     */
    public String getPath ()
    {
        return _path;
    }

    /**
     * Returns the local location of this resource.
     */
    public File getLocal ()
    {
        return _local;
    }

    /**
     * Returns the remote location of this resource.
     */
    public URL getRemote ()
    {
        return _remote;
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
     * Returns true if this resource has an associated "validated" marker
     * file.
     */
    public boolean isMarkedValid ()
    {
        return _marker.exists();
    }

    /**
     * Creates a "validated" marker file for this resource to indicate
     * that its MD5 hash has been computed and compared with the value in
     * the digest file.
     *
     * @throws IOException if we fail to create the marker file.
     */
    public void markAsValid ()
        throws IOException
    {
        _marker.createNewFile();
    }

    /**
     * Removes any "validated" marker file associated with this resource.
     */
    public void clearMarker ()
    {
        if (_marker.exists()) {
            if (!_marker.delete()) {
                Log.warning("Failed to erase marker file '" + _marker + "'.");
            }
        }
    }

    /**
     * Wipes this resource file along with any "validated" marker file
     * that may be associated with it.
     */
    public void erase ()
    {
        clearMarker();
        if (_local.exists()) {
            if (!_local.delete()) {
                Log.warning("Failed to erase resource '" + _local + "'.");
            }
        }
    }

    /**
     * Returns a string representation of this instance.
     */
    public String toString ()
    {
        return _path;
    }

    protected String _path;
    protected URL _remote;
    protected File _local, _marker;

    protected final static int DIGEST_BUFFER_SIZE = 5 * 1025;
}
