//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2016 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.data;

import java.io.*;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.threerings.getdown.util.FileUtil;
import com.threerings.getdown.util.ProgressObserver;
import com.threerings.getdown.util.StringUtil;

import static com.threerings.getdown.Log.log;

/**
 * Models a single file resource used by an {@link Application}.
 */
public class Resource implements Comparable<Resource>
{
    /** Defines special attributes for resources. */
    public static enum Attr {
        /** Indicates that the resource should be unpacked. */
        UNPACK,
        /** Indicates that the resource should be marked executable. */
        EXEC
    };

    public static final EnumSet<Attr> NORMAL = EnumSet.noneOf(Attr.class);
    public static final EnumSet<Attr> UNPACK = EnumSet.of(Attr.UNPACK);
    public static final EnumSet<Attr> EXEC   = EnumSet.of(Attr.EXEC);

    /**
     * Computes the MD5 hash of the supplied file.
     * @param version the version of the digest protocol to use.
     */
    public static String computeDigest (int version, File target, MessageDigest md,
                                        ProgressObserver obs)
        throws IOException
    {
        md.reset();
        byte[] buffer = new byte[DIGEST_BUFFER_SIZE];
        int read;

        boolean isJar = isJar(target.getPath());
        boolean isPacked200Jar = isPacked200Jar(target.getPath());

        // if this is a jar, we need to compute the digest in a "timestamp and file order" agnostic
        // manner to properly correlate jardiff patched jars with their unpatched originals
        if (isJar || isPacked200Jar){
            File tmpJarFile = null;
            JarFile jar = null;
            try {
                // if this is a compressed jar file, uncompress it to compute the jar file digest
                if (isPacked200Jar){
                    tmpJarFile = new File(target.getPath() + ".tmp");
                    FileUtil.unpackPacked200Jar(target, tmpJarFile);
                    jar = new JarFile(tmpJarFile);
                } else{
                    jar = new JarFile(target);
                }

                List<JarEntry> entries = Collections.list(jar.entries());
                Collections.sort(entries, ENTRY_COMP);

                int eidx = 0;
                for (JarEntry entry : entries) {
                    // old versions of the digest code skipped metadata
                    if (version < 2) {
                        if (entry.getName().startsWith("META-INF")) {
                            updateProgress(obs, eidx, entries.size());
                            continue;
                        }
                    }

                    try (InputStream in = jar.getInputStream(entry)) {
                        while ((read = in.read(buffer)) != -1) {
                            md.update(buffer, 0, read);
                        }
                    }

                    updateProgress(obs, eidx, entries.size());
                }

            } finally {
                if (jar != null) {
                    try {
                        jar.close();
                    } catch (IOException ioe) {
                        log.warning("Error closing jar", "path", target, "jar", jar, "error", ioe);
                    }
                }
                if (tmpJarFile != null) {
                    FileUtil.deleteHarder(tmpJarFile);
                }
            }

        } else {
            long totalSize = target.length(), position = 0L;
            try (FileInputStream fin = new FileInputStream(target)) {
                while ((read = fin.read(buffer)) != -1) {
                    md.update(buffer, 0, read);
                    position += read;
                    updateProgress(obs, position, totalSize);
                }
            }
        }
        return StringUtil.hexlate(md.digest());
    }

    /**
     * Creates a resource with the supplied remote URL and local path.
     */
    public Resource (String path, URL remote, File local, EnumSet<Attr> attrs)
    {
        _path = path;
        _remote = remote;
        _local = local;
        _localNew = new File(local.toString() + "_new");
        String lpath = _local.getPath();
        _marker = new File(lpath + "v");

        _attrs = attrs;
        _isJar = isJar(lpath);
        _isPacked200Jar = isPacked200Jar(lpath);
        boolean unpack = attrs.contains(Attr.UNPACK);
        if (unpack && _isJar) {
            _unpacked = _local.getParentFile();
        } else if(unpack && _isPacked200Jar) {
            String dotJar = ".jar", lname = _local.getName();
            String uname = lname.substring(0, lname.lastIndexOf(dotJar) + dotJar.length());
            _unpacked = new File(_local.getParent(), uname);
        }
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
     * Returns the location of the to-be-installed new version of this resource.
     */
    public File getLocalNew ()
    {
        return _localNew;
    }

    /**
     *  Returns the location of the unpacked resource.
     */
    public File getUnpacked ()
    {
        return _unpacked;
    }

    /**
     *  Returns the final target of this resource, whether it has been unpacked or not.
     */
    public File getFinalTarget ()
    {
        return shouldUnpack() ? getUnpacked() : getLocal();
    }

    /**
     * Returns the remote location of this resource.
     */
    public URL getRemote ()
    {
        return _remote;
    }

    /**
     * Returns true if this resource should be unpacked as a part of the validation process.
     */
    public boolean shouldUnpack ()
    {
        return _attrs.contains(Attr.UNPACK) && !SysProps.noUnpack();
    }

    /**
     * Computes the MD5 hash of this resource's underlying file.
     * <em>Note:</em> This is both CPU and I/O intensive.
     * @param version the version of the digest protocol to use.
     */
    public String computeDigest (int version, MessageDigest md, ProgressObserver obs)
        throws IOException
    {
        File file;
        if (_local.toString().toLowerCase(Locale.ROOT).endsWith(Application.CONFIG_FILE)) {
            file = _local;
        } else {
            file = _localNew.exists() ? _localNew : _local;
        }
        return computeDigest(version, file, md, obs);
    }

    /**
     * Returns true if this resource has an associated "validated" marker
     * file.
     */
    public boolean isMarkedValid ()
    {
        if (!_local.exists()) {
            clearMarker();
            return false;
        }
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
        if (_marker.exists() && !FileUtil.deleteHarder(_marker)) {
            log.warning("Failed to erase marker file '" + _marker + "'.");
        }
    }

    /**
     * Installs the {@code getLocalNew} version of this resource to {@code getLocal}.
     */
    public void install () throws IOException {
        File source = getLocalNew(), dest = getLocal();
        log.info("- " + source);
        if (!FileUtil.renameTo(source, dest)) {
            throw new IOException("Failed to rename " + source + " to " + dest);
        }
        applyAttrs();
        markAsValid();
    }

    /**
     * Unpacks this resource file into the directory that contains it.
     */
    public void unpack () throws IOException
    {
        // sanity check
        if (!_isJar && !_isPacked200Jar) {
            throw new IOException("Requested to unpack non-jar file '" + _local + "'.");
        }
        if (_isJar) {
            try (JarFile jar = new JarFile(_local)) {
                FileUtil.unpackJar(jar, _unpacked);
            }
        } else {
            FileUtil.unpackPacked200Jar(_local, _unpacked);
        }
    }

    /**
     * Applies this resources special attributes: unpacks this resource if needed, marks it as
     * executable if needed.
     */
    public void applyAttrs () throws IOException {
        if (shouldUnpack()) {
            unpack();
        }
        if (_attrs.contains(Attr.EXEC)) {
            FileUtil.makeExecutable(_local);
        }
    }

    /**
     * Wipes this resource file along with any "validated" marker file that may be associated with
     * it.
     */
    public void erase ()
    {
        clearMarker();
        if (_local.exists() && !FileUtil.deleteHarder(_local)) {
            log.warning("Failed to erase resource '" + _local + "'.");
        }
    }

    @Override public int compareTo (Resource other) {
        return _path.compareTo(other._path);
    }

    @Override public boolean equals (Object other)
    {
        if (other instanceof Resource) {
            return _path.equals(((Resource)other)._path);
        } else {
            return false;
        }
    }

    @Override public int hashCode ()
    {
        return _path.hashCode();
    }

    @Override public String toString ()
    {
        return _path;
    }

    /** Helper function to simplify the process of reporting progress. */
    protected static void updateProgress (ProgressObserver obs, long pos, long total)
    {
        if (obs != null) {
            obs.progress((int)(100 * pos / total));
        }
    }

    protected static boolean isJar (String path)
    {
        return path.endsWith(".jar") || path.endsWith(".jar_new");
    }

    protected static boolean isPacked200Jar (String path)
    {
        return path.endsWith(".jar.pack") || path.endsWith(".jar.pack_new") ||
            path.endsWith(".jar.pack.gz")|| path.endsWith(".jar.pack.gz_new");
    }

    protected String _path;
    protected URL _remote;
    protected File _local, _localNew, _marker, _unpacked;
    protected EnumSet<Attr> _attrs;
    protected boolean _isJar, _isPacked200Jar;

    /** Used to sort the entries in a jar file. */
    protected static final Comparator<JarEntry> ENTRY_COMP = new Comparator<JarEntry>() {
        @Override public int compare (JarEntry e1, JarEntry e2) {
            return e1.getName().compareTo(e2.getName());
        }
    };

    protected static final int DIGEST_BUFFER_SIZE = 5 * 1025;
}
