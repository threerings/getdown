//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2016 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

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

import com.samskivert.io.StreamUtil;
import com.samskivert.text.MessageUtil;
import com.samskivert.util.StringUtil;

import com.threerings.getdown.util.ConfigUtil;
import com.threerings.getdown.util.ProgressObserver;

import static com.threerings.getdown.Log.log;

/**
 * Manages the <code>digest.txt</code> file and the computing and processing of digests for an
 * application.
 */
public class Digest
{
    /** The current version of the digest protocol. */
    public static final int VERSION = 2;

    /** The current algorithm used to sign digest files. */
    public static final String SIG_ALGO = "SHA256withRSA";

    /**
     * Returns the name of the digest file for the specified protocol version.
     */
    public static String digestFile (int version) {
        String infix = version > 1 ? String.valueOf(version) : "";
        return FILE_NAME + infix + FILE_SUFFIX;
    }

    /**
     * Creates a digest file at the specified location using the supplied list of resources.
     * @param version the version of the digest protocol to use.
     */
    public static void createDigest (int version, List<Resource> resources, File output)
        throws IOException
    {
        MessageDigest md = getMessageDigest(version);
        StringBuilder data = new StringBuilder();
        PrintWriter pout = null;
        try {
            pout = new PrintWriter(new OutputStreamWriter(new FileOutputStream(output), "UTF-8"));

            // compute and append the digest of each resource in the list
            for (Resource rsrc : resources) {
                String path = rsrc.getPath();
                try {
                    String digest = rsrc.computeDigest(version, md, null);
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
            String filename = digestFile(version);
            pout.println(filename + " = " + StringUtil.hexlate(md.digest(contents)));

        } finally {
            StreamUtil.close(pout);
        }
    }

    /**
     * Obtains an appropriate message digest instance for use by the Getdown system.
     */
    public static MessageDigest getMessageDigest (int version)
    {
        String algo = version > 1 ? "SHA-256" : "MD5";
        try {
            return MessageDigest.getInstance(algo);
        } catch (NoSuchAlgorithmException nsae) {
            throw new RuntimeException("JVM does not support " + algo + ". Gurp!");
        }
    }

    /**
     * Creates a digest instance which will parse and validate the digest in the supplied
     * application directory, using the current digest version.
     */
    public Digest (File appdir, boolean strictComments) throws IOException {
        this(appdir, VERSION, strictComments);
    }

    /**
     * Creates a digest instance which will parse and validate the digest in the supplied
     * application directory.
     * @param version the version of the digest protocol to use.
     */
    public Digest (File appdir, int version, boolean strictComments) throws IOException
    {
        // parse and validate our digest file contents
        String filename = digestFile(version);
        StringBuilder data = new StringBuilder();
        File dfile = new File(appdir, filename);
        ConfigUtil.ParseOpts opts = ConfigUtil.createOpts(false);
        opts.strictComments = strictComments;
        // bias = toward key: the key is the filename and could conceivably contain = signs, value
        // is the hex encoded hash which will not contain =
        opts.biasToKey = true;
        for (String[] pair : ConfigUtil.parsePairs(dfile, opts)) {
            if (pair[0].equals(filename)) {
                _metaDigest = pair[1];
                break;
            }
            _digests.put(pair[0], pair[1]);
            note(data, pair[0], pair[1]);
        }

        // we've reached the end, validate our contents
        MessageDigest md = getMessageDigest(version);
        byte[] contents = data.toString().getBytes("UTF-8");
        String hash = StringUtil.hexlate(md.digest(contents));
        if (!hash.equals(_metaDigest)) {
            String err = MessageUtil.tcompose("m.invalid_digest_file", _metaDigest, hash);
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
     * Computes the hash of the specified resource and compares it with the value parsed from
     * the digest file. Logs a message if the resource fails validation.
     *
     * @return true if the resource is valid, false if it failed the digest check or if an I/O
     * error was encountered during the validation process.
     */
    public boolean validateResource (Resource resource, ProgressObserver obs)
    {
        try {
            String chash = resource.computeDigest(VERSION, getMessageDigest(VERSION), obs);
            String ehash = _digests.get(resource.getPath());
            if (chash.equals(ehash)) {
                return true;
            }
            log.info("Resource failed digest check",
                     "rsrc", resource, "computed", chash, "expected", ehash);
        } catch (Throwable t) {
            log.info("Resource failed digest check", "rsrc", resource, "error", t);
        }
        return false;
    }

    /**
     * Returns the digest of the given {@code resource}.
     */
    public String getDigest (Resource resource)
    {
        return _digests.get(resource.getPath());
    }

    /** Used by {@link #createDigest} and {@link Digest}. */
    protected static void note (StringBuilder data, String path, String digest)
    {
        data.append(path).append(" = ").append(digest).append("\n");
    }

    protected HashMap<String, String> _digests = new HashMap<String, String>();
    protected String _metaDigest = "";

    protected static final String FILE_NAME = "digest";
    protected static final String FILE_SUFFIX = ".txt";
}
