//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2014 Three Rings Design, Inc.
// https://raw.github.com/threerings/getdown/master/LICENSE

package com.threerings.getdown.data;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import java.util.HashMap;
import java.util.List;

import com.samskivert.text.MessageUtil;
import com.samskivert.util.StringUtil;

import com.threerings.getdown.util.ConfigUtil;
import com.threerings.getdown.util.ProgressObserver;

import static com.threerings.getdown.Log.log;

/**
 * Manages the <code>digest.txt</code> file and the computing and processing of MD5 digests for an
 * application.
 */
public class Digest
{
    /** The name of our MD5 digest file. */
    public static final String DIGEST_FILE = "digest.txt";

    /**
     * Creates a digest instance which will parse and validate the <code>digest.txt</code> in the
     * supplied application directory.
     */
    public Digest (File appdir)
        throws IOException
    {
        // parse and validate our digest file contents
        StringBuilder data = new StringBuilder();
        File dfile = new File(appdir, DIGEST_FILE);
        for (String[] pair : ConfigUtil.parsePairs(dfile, false)) {
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
            String err = MessageUtil.tcompose("m.invalid_digest_file", _metaDigest, md5);
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
     * Computes the MD5 hash of the specified resource and compares it with the value parsed from
     * the digest file. Logs a message if the resource fails validation.
     *
     * @return true if the resource is valid, false if it failed the digest check or if an I/O
     * error was encountered during the validation process.
     */
    public boolean validateResource (Resource resource, ProgressObserver obs)
    {
        try {
            String cmd5 = resource.computeDigest(getMessageDigest(), obs);
            String emd5 = _digests.get(resource.getPath());
            if (cmd5.equals(emd5)) {
                return true;
            }
            log.info("Resource failed digest check",
                "rsrc", resource, "computed", cmd5, "expected", emd5);
        } catch (Throwable t) {
            log.info("Resource failed digest check", "rsrc", resource, "error", t);
        }
        return false;
    }

    /**
     * Creates a digest file at the specified location using the supplied list of resources.
     */
    public static void createDigest (List<Resource> resources, File output)
        throws IOException
    {
        MessageDigest md = getMessageDigest();
        StringBuilder data = new StringBuilder();
        PrintWriter pout = new PrintWriter(
            new OutputStreamWriter(new FileOutputStream(output), "UTF-8"));

        // compute and append the MD5 digest of each resource in the list
        for (Resource rsrc : resources) {
            String path = rsrc.getPath();
            try {
                String digest = rsrc.computeDigest(md, null);
                note(data, path, digest);
                pout.println(path + " = " + digest);
            } catch (Throwable t) {
                throw (IOException) new IOException(
                    "Error computing digest for: " + rsrc).initCause(t);
            }
        }

        // finally compute and append the digest for the file contents
        md.reset();
        byte[] contents = data.toString().getBytes("UTF-8");
        pout.println(DIGEST_FILE + " = " + StringUtil.hexlate(md.digest(contents)));

        pout.close();
    }

    /**
     * Obtains an appropriate message digest instance for use by the Getdown system.
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
    protected static void note (StringBuilder data, String path, String digest)
    {
        data.append(path).append(" = ").append(digest).append("\n");
    }

    protected HashMap<String, String> _digests = new HashMap<String, String>();
    protected String _metaDigest = "";
}
