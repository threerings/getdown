//
// $Id: Digest.java,v 1.6 2004/07/30 02:23:52 mdb Exp $

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

import com.threerings.getdown.Log;
import com.threerings.getdown.util.ConfigUtil;
import com.threerings.getdown.util.ProgressObserver;

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
        StringBuffer data = new StringBuffer();
        File dfile = new File(appdir, DIGEST_FILE);
        List pairs = ConfigUtil.parsePairs(dfile, false);
        for (Iterator iter = pairs.iterator(); iter.hasNext(); ) {
            String[] pair = (String[])iter.next();
            if (pair[0].equals(DIGEST_FILE)) {
                _metaDigest = pair[1];
                break;
            }
            _digests.put(pair[0], pair[1]);
            note(data, pair[0], pair[1]);
        }

        // we've reached the end, validate our contents
        MessageDigest md = getMessageDigest();
        byte[] contents = data.toString().getBytes("UTF-8");
        String md5 = StringUtil.hexlate(md.digest(contents));
        if (!md5.equals(_metaDigest)) {
            String err = MessageUtil.tcompose(
                "m.invalid_digest_file", _metaDigest, md5);
            throw new IOException(err);
        }
    }

    /**
     * Returns the digest for the digest file.
     */
    public String getMetaDigest ()
    {
        return _metaDigest;
    }

    /**
     * Computes the MD5 hash of the specified resource and compares it
     * with the value parsed from the digest file. Logs a message if the
     * resource fails validation.
     *
     * @return true if the resource is valid, false if it failed the
     * digest check or if an I/O error was encountered during the
     * validation process.
     */
    public boolean validateResource (Resource resource, ProgressObserver obs)
    {
        try {
            String cmd5 = resource.computeDigest(getMessageDigest(), obs);
            String emd5 = (String)_digests.get(resource.getPath());
            if (cmd5.equals(emd5)) {
                return true;
            }
            Log.info("Resource failed digest check [rsrc=" + resource +
                     ", computed=" + cmd5 + ", expected=" + emd5 + "].");
        } catch (IOException ioe) {
            Log.info("Resource failed digest check [rsrc=" + resource +
                     ", error=" + ioe + "].");
        }
        return false;
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
            String digest = rsrc.computeDigest(md, null);
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

    /**
     * Obtains an appropriate message digest instance for use by the
     * Getdown system.
     */
    public static MessageDigest getMessageDigest ()
    {
        try {
            return MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException nsae) {
            throw new RuntimeException("JVM does not support MD5. Gurp!");
        }
    }

    /** Used by {@link #createDigest} and {@link Digest}. */
    protected static void note (StringBuffer data, String path, String digest)
    {
        data.append(path).append(" = ").append(digest).append("\n");
    }

    protected HashMap _digests = new HashMap();
    protected String _metaDigest = "";
}
