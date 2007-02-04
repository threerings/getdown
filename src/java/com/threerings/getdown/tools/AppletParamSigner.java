//
// $Id$
//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2006 Three Rings Design, Inc.
//
// This program is free software; you can redistribute it and/or modify it
// under the terms of the GNU General Public License as published by the Free
// Software Foundation; either version 2 of the License, or (at your option)
// any later version.
//
// This program is distributed in the hope that it will be useful, but WITHOUT
// ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
// FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
// more details.
//
// You should have received a copy of the GNU General Public License along with
// this program; if not, write to the: Free Software Foundation, Inc.,
// 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA

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
