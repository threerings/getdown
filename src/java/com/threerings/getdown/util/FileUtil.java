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

package com.threerings.getdown.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import com.samskivert.io.StreamUtil;

import org.apache.commons.io.IOUtils;

import com.threerings.getdown.Log;

/**
 * File related utilities.
 */
public class FileUtil
{
    /**
     * Gets the specified source file to the specified destination file by hook or crook. Windows
     * has all sorts of problems which we work around in this method.
     *
     * @return true if we managed to get the job done, false otherwise.
     */
    public static boolean renameTo (File source, File dest)
    {
        // if we're on a civilized operating system we may be able to simple rename it
        if (source.renameTo(dest)) {
            return true;
        }

        // fall back to trying to rename the old file out of the way, rename the new file into
        // place and then delete the old file
        if (dest.exists()) {
            File temp = new File(dest.getPath() + "_old");
            if (temp.exists()) {
                if (!temp.delete()) {
                    Log.warning("Failed to delete old intermediate file " + temp + ".");
                    // the subsequent code will probably fail
                }
            }
            if (dest.renameTo(temp)) {
                if (source.renameTo(dest)) {
                    if (temp.delete()) {
                        Log.warning("Failed to delete intermediate file " + temp + ".");
                    }
                    return true;
                }
            }
        }

        // as a last resort, try copying the old data over the new
        FileInputStream fin = null;
        FileOutputStream fout = null;
        try {
            fin = new FileInputStream(source);
            fout = new FileOutputStream(dest);
            IOUtils.copy(fin, fout);
            if (!source.delete()) {
                Log.warning("Failed to delete " + source +
                            " after brute force copy to " + dest + ".");
            }
            return true;

        } catch (IOException ioe) {
            Log.warning("Failed to copy " + source + " to " + dest + ": " + ioe);
            return false;

        } finally {
            StreamUtil.close(fin);
            StreamUtil.close(fout);
        }
    }
}
