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

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;

import org.apache.commons.codec.binary.Base64;

/**
 * Produces a signed hash of the appbase, appname, and image path to ensure that signed copies of
 * Getdown are not hijacked to run malicious code.
 */
public class AppletParamSigner
{
    public static void main (String[] args)
    {
        try {
            if (args.length != 7) {
                System.err.println("AppletParamSigner keystore storepass alias keypass " +
                                   "appbase appname imgpath");
                System.exit(255);
            }

            String keystore = args[0];
            String storepass = args[1];
            String alias = args[2];
            String keypass = args[3];
            String appbase = args[4];
            String appname = args[5];
            String imgpath = args[6];
            String params = appbase + appname + imgpath;

            KeyStore store = KeyStore.getInstance("JKS");
            store.load(new BufferedInputStream(new FileInputStream(keystore)),
                       storepass.toCharArray());
            PrivateKey key = (PrivateKey)store.getKey(alias, keypass.toCharArray());
            Signature sig = Signature.getInstance("SHA1withRSA");
            sig.initSign(key);
            sig.update(params.getBytes());
            String signed = new String(Base64.encodeBase64(sig.sign()));
            System.out.println("<param name=\"appbase\" value=\"" + appbase + "\" />");
            System.out.println("<param name=\"appname\" value=\"" + appname + "\" />");
            System.out.println("<param name=\"bgimage\" value=\"" + imgpath + "\" />");
            System.out.println("<param name=\"signature\" value=\"" + signed + "\" />");

        } catch (Exception e) {
            System.err.println("Failed to produce signature.");
            e.printStackTrace();
        }
    }
}
