//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2014 Three Rings Design, Inc.
// https://raw.github.com/threerings/getdown/master/LICENSE

package com.threerings.getdown.net;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URLConnection;
import java.util.List;

import com.samskivert.io.StreamUtil;

import com.threerings.getdown.data.Resource;
import com.threerings.getdown.util.ConnectionUtil;

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

    @Override
    protected long checkSize (Resource rsrc)
        throws IOException
    {
        URLConnection conn = ConnectionUtil.open(rsrc.getRemote());
        try {
            // if we're accessing our data via HTTP, we only need a HEAD request
            if (conn instanceof HttpURLConnection) {
                HttpURLConnection hcon = (HttpURLConnection)conn;
                hcon.setRequestMethod("HEAD");
                hcon.connect();
                // make sure we got a satisfactory response code
                if (hcon.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    throw new IOException("Unable to check up-to-date for " +
                                          rsrc.getRemote() + ": " + hcon.getResponseCode());
                }
            }
            return conn.getContentLength();

        } finally {
            // let it be known that we're done with this connection
            conn.getInputStream().close();
        }
    }

    @Override
    protected void doDownload (Resource rsrc)
        throws IOException
    {
        // download the resource from the specified URL
        URLConnection conn = ConnectionUtil.open(rsrc.getRemote());
        conn.connect();

        // make sure we got a satisfactory response code
        if (conn instanceof HttpURLConnection) {
            HttpURLConnection hcon = (HttpURLConnection)conn;
            if (hcon.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new IOException("Unable to download resource " + rsrc.getRemote() + ": " +
                                      hcon.getResponseCode());
            }
        }

        long actualSize = conn.getContentLength();
        log.info("Downloading resource", "url", rsrc.getRemote(), "size", actualSize);
        InputStream in = null;
        FileOutputStream out = null;
        long currentSize = 0L;
        try {
            in = conn.getInputStream();
            out = new FileOutputStream(rsrc.getLocal());
            int read;

            // TODO: look to see if we have a download info file
            // containing info on potentially partially downloaded data;
            // if so, use a "Range: bytes=HAVE-" header.

            // read in the file data
            while ((read = in.read(_buffer)) != -1) {
                // write it out to our local copy
                out.write(_buffer, 0, read);

                // if we have no observer, then don't bother computing download statistics
                if (_obs == null) {
                    continue;
                }

                // note that we've downloaded some data
                currentSize += read;
                updateObserver(rsrc, currentSize, actualSize);
            }
        } finally {
            StreamUtil.close(in);
            StreamUtil.close(out);
        }
    }
}
