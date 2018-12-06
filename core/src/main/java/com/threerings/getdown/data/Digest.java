//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2018 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.data;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.*;

import com.threerings.getdown.util.Config;
import com.threerings.getdown.util.MessageUtil;
import com.threerings.getdown.util.ProgressObserver;
import com.threerings.getdown.util.StringUtil;

import static com.threerings.getdown.Log.log;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Manages the <code>digest.txt</code> file and the computing and processing of digests for an
 * application.
 */
public class Digest
{
    /** The current version of the digest protocol. */
    public static final int VERSION = 2;

    /**
     * Returns the name of the digest file for the specified protocol version.
     */
    public static String digestFile (int version) {
        String infix = version > 1 ? String.valueOf(version) : "";
        return FILE_NAME + infix + FILE_SUFFIX;
    }

    /**
     * Returns the crypto algorithm used to sign digest files of the specified version.
     */
    public static String sigAlgorithm (int version) {
        switch (version) {
        case 1: return "SHA1withRSA";
        case 2: return "SHA256withRSA";
        default: throw new IllegalArgumentException("Invalid digest version " + version);
        }
    }

    /**
     * Creates a digest file at the specified location using the supplied list of resources.
     * @param version the version of the digest protocol to use.
     */
    public static void createDigest (int version, List<Resource> resources, File output)
        throws IOException
    {
        // first compute the digests for all the resources in parallel
        ExecutorService exec = Executors.newFixedThreadPool(SysProps.threadPoolSize());
        final Map<Resource, String> digests = new ConcurrentHashMap<>();
        final BlockingQueue<Object> completed = new LinkedBlockingQueue<>();
        final int fversion = version;

        long start = System.currentTimeMillis();

        Set<Resource> pending = new HashSet<>(resources);
        for (final Resource rsrc : resources) {
            exec.execute(new Runnable() {
                public void run () {
                    try {
                        MessageDigest md = getMessageDigest(fversion);
                        digests.put(rsrc, rsrc.computeDigest(fversion, md, null));
                        completed.add(rsrc);
                    } catch (Throwable t) {
                        completed.add(new IOException("Error computing digest for: " + rsrc).
                                      initCause(t));
                    }
                }
            });
        }

        // queue a shutdown of the thread pool when the tasks are done
        exec.shutdown();

        try {
            while (pending.size() > 0) {
                Object done = completed.poll(600, TimeUnit.SECONDS);
                if (done instanceof IOException) {
                    throw (IOException)done;
                } else if (done instanceof Resource) {
                    pending.remove((Resource)done);
                } else {
                    throw new AssertionError("What is this? " + done);
                }
            }
        } catch (InterruptedException ie) {
            throw new IOException("Timeout computing digests. Wow.");
        }

        StringBuilder data = new StringBuilder();
        try (FileOutputStream fos = new FileOutputStream(output);
             OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
             PrintWriter pout = new PrintWriter(osw)) {
            // compute and append the digest of each resource in the list
            for (Resource rsrc : resources) {
                String path = rsrc.getPath();
                String digest = digests.get(rsrc);
                note(data, path, digest);
                pout.println(path + " = " + digest);
            }
            // finally compute and append the digest for the file contents
            MessageDigest md = getMessageDigest(version);
            byte[] contents = data.toString().getBytes(UTF_8);
            String filename = digestFile(version);
            pout.println(filename + " = " + StringUtil.hexlate(md.digest(contents)));
        }

        long elapsed = System.currentTimeMillis() - start;
        log.debug("Computed digests [rsrcs=" + resources.size() + ", time=" + elapsed + "ms]");
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
        Config.ParseOpts opts = Config.createOpts(false);
        opts.strictComments = strictComments;
        // bias = toward key: the key is the filename and could conceivably contain = signs, value
        // is the hex encoded hash which will not contain =
        opts.biasToKey = true;
        for (String[] pair : Config.parsePairs(dfile, opts)) {
            if (pair[0].equals(filename)) {
                _metaDigest = pair[1];
                break;
            }
            _digests.put(pair[0], pair[1]);
            note(data, pair[0], pair[1]);
        }

        // we've reached the end, validate our contents
        MessageDigest md = getMessageDigest(version);
        byte[] contents = data.toString().getBytes(UTF_8);
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

    protected HashMap<String, String> _digests = new HashMap<>();
    protected String _metaDigest = "";

    protected static final String FILE_NAME = "digest";
    protected static final String FILE_SUFFIX = ".txt";
}
