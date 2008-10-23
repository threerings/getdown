//
// $Id$
//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2008 Three Rings Design, Inc.
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
package com.threerings.getdown.net;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.List;

import com.samskivert.io.StreamUtil;

import com.threerings.getdown.data.Resource;

import static com.threerings.getdown.Log.log;

/**
 * Implements downloading files over HTTP
 */
public class HTTPDownloader extends Downloader
{
    public HTTPDownloader (List<Resource> resources, Observer obs)
    {
        super(resources, obs);
    }

    /**
     * A method of instantiating a downloader to take over the job partway.
     */
    public HTTPDownloader (List<Resource> resources, Observer obs,
        long totalSize)
    {
        super(resources, obs);
        _totalSize = totalSize;
        _start = System.currentTimeMillis();
    }

    /**
     * Issues a HEAD request for the specified resource and notes the
     * amount of data we will be downloading to account for it.
     */
    protected long checkSize (Resource rsrc)
        throws IOException
    {
        // read the file information via an HTTP HEAD request
        HttpURLConnection ucon = (HttpURLConnection)
            rsrc.getRemote().openConnection();
        ucon.setRequestMethod("HEAD");
        ucon.connect();

        // make sure we got a satisfactory response code
        if (ucon.getResponseCode() != HttpURLConnection.HTTP_OK) {
            String errmsg = "Unable to check up-to-date for " +
                rsrc.getRemote() + ": " + ucon.getResponseCode();
            throw new IOException(errmsg);
        }

        return ucon.getContentLength();
    }

    // documentation inherited
    protected void doDownload (Resource rsrc)
        throws IOException
    {
        // download the resource from the specified URL
        HttpURLConnection ucon = (HttpURLConnection)rsrc.getRemote().openConnection();
        ucon.connect();

        // make sure we got a satisfactory response code
        if (ucon.getResponseCode() != HttpURLConnection.HTTP_OK) {
            String errmsg = "Unable to download resource " +
                rsrc.getRemote() + ": " + ucon.getResponseCode();
            throw new IOException(errmsg);
        }

        log.info("Downloading resource [url=" + rsrc.getRemote() + "].");
        InputStream in = null;
        FileOutputStream out = null;
        try {
            in = ucon.getInputStream();
            out = new FileOutputStream(rsrc.getLocal());
            int read;

            // TODO: look to see if we have a download info file
            // containing info on potentially partially downloaded data;
            // if so, use a "Range: bytes=HAVE-" header.

            // read in the file data
            while ((read = in.read(_buffer)) != -1) {
                // write it out to our local copy
                out.write(_buffer, 0, read);

                // if we have no observer, then don't bother computing
                // download statistics
                if (_obs == null) {
                    continue;
                }

                // note that we've downloaded some data
                _currentSize += read;
                updateObserver();
            }
        } finally {
            StreamUtil.close(in);
            StreamUtil.close(out);
        }
    }
}
