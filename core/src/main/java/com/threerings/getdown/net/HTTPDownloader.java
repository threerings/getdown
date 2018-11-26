//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2016 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.net;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.Collection;

import com.threerings.getdown.data.Resource;
import com.threerings.getdown.util.ConnectionUtil;

import static com.threerings.getdown.Log.log;

/**
 * Implements downloading files over HTTP
 */
public class HTTPDownloader extends Downloader
{
    public HTTPDownloader (Collection<Resource> resources, Observer obs)
    {
        super(resources, obs);
    }

    @Override
    protected long checkSize (Resource rsrc)
        throws IOException
    {
        URLConnection conn = ConnectionUtil.open(rsrc.getRemote(), 0, 0);
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

        log.info("Downloading resource", "url", rsrc.getRemote(), "size", -1);

        URL remoteURL = rsrc.getRemote();
        File localNew = rsrc.getLocalNew();


        try(ReadableByteChannel rbc = Channels.newChannel(remoteURL.openStream());
            FileOutputStream fos = new FileOutputStream(localNew)) {

            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
            updateObserver(rsrc, localNew.length(), localNew.length());
        }

    }
}
