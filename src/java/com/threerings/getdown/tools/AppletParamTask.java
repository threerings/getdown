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
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;

import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;

import org.apache.commons.codec.binary.Base64;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;

/**
 * Computes and fills in signed applet parameters. The input file should contain the following
 * tokens: <code>@APPBASE@, @APPNAME@, @IMGPATH@, @SIGNATURE@</code> which will be filled in based
 * on supplied and computed values.
 */
public class AppletParamTask extends Task
{
    public void setAppbase (String appbase)
    {
        _appbase = appbase;
    }

    public void setAppname (String appname)
    {
        _appname = appname;
    }

    public void setImgpath (String imgpath)
    {
        _imgpath = imgpath;
    }

    public void setKeystore (File keystore)
    {
        _keystore = keystore;
    }

    public void setStorepass (String storepass)
    {
        _storepass = storepass;
    }

    public void setAlias (String alias)
    {
        _alias = alias;
    }

    public void setKeypass (String keypass)
    {
        _keypass = keypass;
    }

    public void setFile (File file)
    {
        _input = file;
    }

    public void setTofile (File tofile)
    {
        _output = tofile;
    }

    public void execute () throws BuildException
    {
        String params = _appbase + _appname + _imgpath;

        try {
            KeyStore store = KeyStore.getInstance("JKS");
            store.load(new BufferedInputStream(new FileInputStream(_keystore)),
                       _storepass.toCharArray());
            PrivateKey key = (PrivateKey)store.getKey(_alias, _keypass.toCharArray());
            Signature sig = Signature.getInstance("SHA1withRSA");
            sig.initSign(key);
            sig.update(params.getBytes());
            String signature = new String(Base64.encodeBase64(sig.sign()));

            BufferedReader bin = new BufferedReader(new FileReader(_input));
            PrintWriter pout = new PrintWriter(new FileWriter(_output));
            String line;
            while ((line = bin.readLine()) != null) {
                line = line.replaceAll("@APPNAME@", _appname);
                line = line.replaceAll("@APPBASE@", _appbase);
                line = line.replaceAll("@IMGPATH@", _imgpath);
                line = line.replaceAll("@SIGNATURE@", signature);
                pout.println(line);
            }

            bin.close();
            pout.close();

        } catch (Exception e) {
            throw new BuildException(e);
        }
    }

    protected String _appbase, _appname, _imgpath;
    protected File _keystore, _input, _output;
    protected String _storepass = "", _alias, _keypass = "";
}
