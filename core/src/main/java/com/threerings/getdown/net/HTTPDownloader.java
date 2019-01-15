//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2018 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.net;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

import com.threerings.getdown.data.Resource;
import com.threerings.getdown.util.ConnectionUtil;

import static com.threerings.getdown.Log.log;

/**
 * Implements downloading files over HTTP
 */
public class HTTPDownloader extends Downloader
{
    public HTTPDownloader (Proxy proxy)
    {
        _proxy = proxy;
    }

    @Override protected long checkSize (Resource rsrc) throws IOException
    {
        URLConnection conn = ConnectionUtil.open(_proxy, rsrc.getRemote(), 0, 0);
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

    @Override protected void download (Resource rsrc) throws IOException
    {
        // TODO: make FileChannel download impl (below) robust and allow apps to opt-into it via a
        // system property
        if (true) {
            // download the resource from the specified URL
            URLConnection conn = ConnectionUtil.open(_proxy, rsrc.getRemote(), 0, 0);
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
            long currentSize = 0L;
            byte[] buffer = new byte[4*4096];
            try (InputStream in = conn.getInputStream();
                 FileOutputStream out = new FileOutputStream(rsrc.getLocalNew())) {

                // TODO: look to see if we have a download info file
                // containing info on potentially partially downloaded data;
                // if so, use a "Range: bytes=HAVE-" header.

                // read in the file data
                int read;
                while ((read = in.read(buffer)) != -1) {
                    // abort the download if the downloader is aborted
                    if (_state == State.ABORTED) {
                        break;
                    }
                    // write it out to our local copy
                    out.write(buffer, 0, read);
                    // note that we've downloaded some data
                    currentSize += read;
                    reportProgress(rsrc, currentSize, actualSize);
                }
            }

        } else {
            log.info("Downloading resource", "url", rsrc.getRemote(), "size", "unknown");
            File localNew = rsrc.getLocalNew();
            try (ReadableByteChannel rbc = Channels.newChannel(rsrc.getRemote().openStream());
                 FileOutputStream fos = new FileOutputStream(localNew)) {
                // TODO: more work is needed here, transferFrom can fail to transfer the entire
                // file, in which case it's not clear what we're supposed to do.. call it again?
                // will it repeatedly fail?
                fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                reportProgress(rsrc, localNew.length(), localNew.length());
            }
        }
    }

    protected final Proxy _proxy;
}
