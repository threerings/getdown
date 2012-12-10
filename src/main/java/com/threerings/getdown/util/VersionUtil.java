//
// $Id$
//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2010 Three Rings Design, Inc.
// http://code.google.com/p/getdown/
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

package com.threerings.getdown.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Reader;

import com.samskivert.io.StreamUtil;
import com.samskivert.util.StringUtil;

import static com.threerings.getdown.Log.log;

/**
 * Version related utilities.
 */
public class VersionUtil
{
    /**
     * Reads a version number from a file.
     */
    public static long readVersion (File vfile)
    {
        FileInputStream fin = null;
        long fileVersion = -1;
        try {
            fin = new FileInputStream(vfile);
            BufferedReader bin = new BufferedReader(new InputStreamReader(fin));
            String vstr = bin.readLine();
            if (!StringUtil.isBlank(vstr)) {
                fileVersion = Long.parseLong(vstr);
            }
        } catch (Exception e) {
            log.info("Unable to read version file: " + e.getMessage());
        } finally {
            StreamUtil.close(fin);
        }

        return fileVersion;
    }

    /**
     * Writes a version number to a file.
     */
    public static void writeVersion (File vfile, long version)
        throws IOException
    {
        PrintStream out = new PrintStream(new FileOutputStream(vfile));
        try {
            out.println(version);
        } catch (Exception e) {
            log.warning("Unable to write version file: " + e.getMessage());
        } finally {
            StreamUtil.close(out);
        }
    }
}
