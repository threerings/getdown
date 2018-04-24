//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2016 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.codec.binary.Base64;

import com.samskivert.io.StreamUtil;
import com.threerings.getdown.data.Application;
import com.threerings.getdown.data.Digest;
import com.threerings.getdown.data.Resource;

/**
 * Handles the generation of the digest.txt file.
 */
public class Digester
{
    /**
     * A command line entry point for the digester.
     */
    public static void main (String[] args)
        throws IOException, GeneralSecurityException
    {
        switch (args.length) {
        case 1:
            createDigests(new File(args[0]), null, null, null);
            break;
        case 4:
            createDigests(new File(args[0]), new File(args[1]), args[2], args[3]);
            break;
        default:
            System.err.println("Usage: Digester app_dir [keystore_path password alias]");
            System.exit(255);
        }
    }

    /**
     * Creates digest file(s) and optionally signs them if {@code keystore} is not null.
     */
    public static void createDigests (File appdir, File keystore, String password, String alias)
        throws IOException, GeneralSecurityException
    {
        for (int version = 1; version <= Digest.VERSION; version++) {
            createDigest(version, appdir);
            if (keystore != null) {
                signDigest(version, appdir, keystore, password, alias);
            }
        }
    }

    /**
     * Creates a digest file in the specified application directory.
     */
    public static void createDigest (int version, File appdir)
        throws IOException
    {
        File target = new File(appdir, Digest.digestFile(version));
        System.out.println("Generating digest file '" + target + "'...");

        // create our application and instruct it to parse its business
        Application app = new Application(appdir, null);
        app.init(false);

        List<Resource> rsrcs = new ArrayList<>();
        rsrcs.add(app.getConfigResource());
        rsrcs.addAll(app.getCodeResources());
        rsrcs.addAll(app.getResources());
        for (Application.AuxGroup ag : app.getAuxGroups()) {
            rsrcs.addAll(ag.codes);
            rsrcs.addAll(ag.rsrcs);
        }

        // now generate the digest file
        Digest.createDigest(version, rsrcs, target);
    }

    /**
     * Creates a digest file in the specified application directory.
     */
    public static void signDigest (int version, File appdir,
                                   File storePath, String storePass, String storeAlias)
        throws IOException, GeneralSecurityException
    {
        String filename = Digest.digestFile(version);
        File inputFile = new File(appdir, filename);
        File signatureFile = new File(appdir, filename + Application.SIGNATURE_SUFFIX);

        FileInputStream storeInput = null, dataInput = null;
        FileOutputStream signatureOutput = null;
        try {
            // initialize the keystore
            KeyStore store = KeyStore.getInstance("JKS");
            storeInput = new FileInputStream(storePath);
            store.load(storeInput, storePass.toCharArray());
            PrivateKey key = (PrivateKey)store.getKey(storeAlias, storePass.toCharArray());

            // sign the digest file
            String algo = Digest.sigAlgorithm(version);
            Signature sig = Signature.getInstance(algo);
            dataInput = new FileInputStream(inputFile);
            byte[] buffer = new byte[8192];
            int length;

            sig.initSign(key);
            while ((length = dataInput.read(buffer)) != -1) {
                sig.update(buffer, 0, length);
            }

            // Write out the signature
            signatureOutput = new FileOutputStream(signatureFile);
            String signed = new String(Base64.encodeBase64(sig.sign()));
            signatureOutput.write(signed.getBytes("utf8"));

        } finally {
            StreamUtil.close(signatureOutput);
            StreamUtil.close(dataInput);
            StreamUtil.close(storeInput);
        }
    }
}
