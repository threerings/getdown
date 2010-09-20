//
// $Id$
//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2010 Three Rings Design, Inc.
//
// Redistribution and use in source and binary forms, with or without modification, are permitted
// provided that the following conditions are met:
// 
// 1. Redistributions of source code must retain the above copyright notice, this list of
//    conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright notice, this list of
//    conditions and the following disclaimer in the documentation and/or other materials provided
//    with the distribution.
// 
// THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES,
// INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
// PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT,
// INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
// TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
// LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
// SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

package com.threerings.getdown.tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;

import org.apache.commons.codec.binary.Base64;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;

import com.threerings.getdown.data.Application;
import com.threerings.getdown.data.Digest;
import com.threerings.getdown.data.Resource;

/**
 * An ant task used to create a <code>digest.txt</code> for a Getdown
 * application deployment.
 */
public class DigesterTask extends Task
{
    /**
     * Sets the application directory.
     */
    public void setAppdir (File appdir)
    {
        _appdir = appdir;
    }

    /**
     * Sets the digest signing keystore.
     */
    public void setKeystore (File path)
    {
        _storepath = path;
    }

    /**
     * Sets the keystore decryption key.
     */
    public void setStorepass (String password)
    {
        _storepass = password;
    }

    /**
     * Sets the private key alias.
     */
    public void setAlias (String alias)
    {
        _storealias = alias;
    }

    /**
     * Performs the actual work of the task.
     */
    @Override
    public void execute () throws BuildException
    {
        // make sure appdir is set
        if (_appdir == null) {
            throw new BuildException("Must specify the path to the application directory " +
                                     "via the 'appdir' attribute.");
        }

        // make sure _storepass and _keyalias are set, if _storepath is set
        if (_storepath != null) {
            if (_storepass == null || _storealias == null) {
                throw new BuildException(
                    "Must specify both a keystore password and a private key alias.");
            }
        }

        try {
            createDigest(_appdir);
            if (_storepath != null) {
                signDigest(_appdir, _storepath, _storepass, _storealias);
            }
        } catch (IOException ioe) {
            throw new BuildException("Error creating digest: " + ioe.getMessage(), ioe);
        } catch (GeneralSecurityException gse) {
            throw new BuildException("Error creating signature: " + gse.getMessage(), gse);
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

        ArrayList<Resource> rsrcs = new ArrayList<Resource>();
        rsrcs.add(app.getConfigResource());
        rsrcs.addAll(app.getCodeResources());
        rsrcs.addAll(app.getResources());
        for (String auxgroup : app.getAuxGroups()) {
            rsrcs.addAll(app.getResources(auxgroup));
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

    /** The application directory in which we're creating a digest file. */
    protected File _appdir;

    /** The path to the keystore we'll use to sign the digest file, if any. */
    protected File _storepath;

    /** The decryption key for the keystore. */
    protected String _storepass;

    /** The private key alias. */
    protected String _storealias;
}
