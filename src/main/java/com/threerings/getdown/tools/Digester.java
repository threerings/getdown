//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2014 Three Rings Design, Inc.
// https://raw.github.com/threerings/getdown/master/LICENSE

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
        if (args.length != 1 && args.length != 4) {
            System.err.println("Usage: Digester app_dir [keystore_path password alias]");
            System.exit(255);
        }

        createDigest(new File(args[0]));
        if (args.length == 4) {
            signDigest(new File(args[0]), new File(args[1]), args[2], args[3]);
        }
    }

    /**
     * Creates a digest file in the specified application directory.
     */
    public static void createDigest (File appdir)
        throws IOException
    {
        File target = new File(appdir, Digest.DIGEST_FILE);
        System.out.println("Generating digest file '" + target + "'...");

        // create our application and instruct it to parse its business
        Application app = new Application(appdir, null);
        app.init(false);

        List<Resource> rsrcs = new ArrayList<Resource>();
        rsrcs.add(app.getConfigResource());
        rsrcs.addAll(app.getCodeResources());
        rsrcs.addAll(app.getResources());
        for (Application.AuxGroup ag : app.getAuxGroups()) {
            rsrcs.addAll(ag.codes);
            rsrcs.addAll(ag.rsrcs);
        }

        // now generate the digest file
        Digest.createDigest(rsrcs, target);
    }

    /**
     * Creates a digest file in the specified application directory.
     */
    public static void signDigest (File appdir, File storePath, String storePass, String storeAlias)
        throws IOException, GeneralSecurityException
    {
        File inputFile = new File(appdir, Digest.DIGEST_FILE);
        File signatureFile = new File(appdir, Digest.DIGEST_FILE + Application.SIGNATURE_SUFFIX);

        // initialize the keystore
        KeyStore store = KeyStore.getInstance("JKS");
        FileInputStream storeInput = new FileInputStream(storePath);
        store.load(storeInput, storePass.toCharArray());
        PrivateKey key = (PrivateKey)store.getKey(storeAlias, storePass.toCharArray());

        // sign the digest file
        Signature sig = Signature.getInstance("SHA1withRSA");
        FileInputStream dataInput = new FileInputStream(inputFile);
        byte[] buffer = new byte[8192];
        int length;

        sig.initSign(key);
        while ((length = dataInput.read(buffer)) != -1) {
            sig.update(buffer, 0, length);
        }

        // Write out the signature
        FileOutputStream signatureOutput = new FileOutputStream(signatureFile);
        String signed = new String(Base64.encodeBase64(sig.sign()));
        signatureOutput.write(signed.getBytes("utf8"));
    }
}
