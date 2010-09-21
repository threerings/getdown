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
import java.io.IOException;

import java.security.GeneralSecurityException;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;

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
            Digester.createDigest(_appdir);
            if (_storepath != null) {
                Digester.signDigest(_appdir, _storepath, _storepass, _storealias);
            }
        } catch (IOException ioe) {
            throw new BuildException("Error creating digest: " + ioe.getMessage(), ioe);
        } catch (GeneralSecurityException gse) {
            throw new BuildException("Error creating signature: " + gse.getMessage(), gse);
        }
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
