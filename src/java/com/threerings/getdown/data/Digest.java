//
// $Id: Digest.java,v 1.1 2004/07/02 11:01:21 mdb Exp $

package com.threerings.getdown.data;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import com.samskivert.text.MessageUtil;
import com.samskivert.util.StringUtil;

import com.threerings.getdown.util.ConfigUtil;

/**
 * Manages the <code>digest.txt</code> file and the computing and
 * processing of MD5 digests for an application.
 */
public class Digest
{
    /** The name of our MD5 digest file. */
    public static final String DIGEST_FILE = "digest.txt";

    /**
     * Creates a digest instance which will parse and validate the
     * <code>digest.txt</code> in the supplied application directory.
     */
    public Digest (File appdir)
        throws IOException
    {
        // parse and validate our digest file contents
        String metaDigest = "";
        StringBuffer data = new StringBuffer();
        List pairs = ConfigUtil.parsePairs(new File(appdir, DIGEST_FILE));
        for (Iterator iter = pairs.iterator(); iter.hasNext(); ) {
            String[] pair = (String[])iter.next();
            if (pair[0].equals(DIGEST_FILE)) {
                metaDigest = pair[1];
                break;
            }
            _digests.put(pair[0], pair[1]);
            note(data, pair[0], pair[1]);
        }

        // we've reached the end, validate our contents
        MessageDigest md = getMessageDigest();
        byte[] contents = data.toString().getBytes("UTF-8");
        String md5 = StringUtil.hexlate(md.digest(contents));
        if (!md5.equals(metaDigest)) {
            String err = MessageUtil.tcompose(
                "m.invalid_digest_file", metaDigest, md5);
            throw new IOException(err);
        }
    }

    /**
     * Returns the stored digest value for the file with the supplied
     * path, or null if no digest exists for a file with that path.
     */
    public String getDigest (String path)
    {
        return (String)_digests.get(path);
    }

    /**
     * Creates a digest file at the specified location using the supplied
     * list of resources.
     */
    public static void createDigest (List resources, File output)
        throws IOException
    {
        MessageDigest md = getMessageDigest();
        StringBuffer data = new StringBuffer();
        PrintWriter pout = new PrintWriter(
            new OutputStreamWriter(new FileOutputStream(output), "UTF-8"));

        // compute and append the MD5 digest of each resource in the list
        for (Iterator iter = resources.iterator(); iter.hasNext(); ) {
            Resource rsrc = (Resource)iter.next();
            String path = rsrc.getPath();
            String digest = rsrc.computeDigest(md);
            note(data, path, digest);
            pout.println(path + " = " + digest);
        }

        // finally compute and append the digest for the file contents
        md.reset();
        byte[] contents = data.toString().getBytes("UTF-8");
        pout.println(DIGEST_FILE + " = " +
                     StringUtil.hexlate(md.digest(contents)));

        pout.close();
    }

    /** Used by {@link #createDigest} and {@link Digest}. */
    protected static void note (StringBuffer data, String path, String digest)
    {
        data.append(path).append(" = ").append(digest).append("\n");
    }

    /** Used by {@link #createDigest} and {@link Digest}. */
    protected static MessageDigest getMessageDigest ()
    {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException nsae) {
            throw new RuntimeException("JVM does not support MD5. Gurp!");
        }
    }

    protected HashMap _digests = new HashMap();
}
