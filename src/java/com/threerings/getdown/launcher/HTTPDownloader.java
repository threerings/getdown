package com.threerings.getdown.launcher;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.List;

import com.samskivert.io.StreamUtil;
import com.threerings.getdown.Log;
import com.threerings.getdown.data.Resource;

public class HTTPDownloader extends Downloader
{
    public HTTPDownloader (List<Resource> resources, Observer obs)
    {
        super(resources, obs);
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

    protected void doDownload (Resource rsrc)
        throws IOException
    {
        // download the resource from the specified URL
        HttpURLConnection ucon = (HttpURLConnection)
            rsrc.getRemote().openConnection();
        ucon.connect();

        // make sure we got a satisfactory response code
        if (ucon.getResponseCode() != HttpURLConnection.HTTP_OK) {
            String errmsg = "Unable to download resource " +
                rsrc.getRemote() + ": " + ucon.getResponseCode();
            throw new IOException(errmsg);
        }

        Log.info("Downloading resource [url=" + rsrc.getRemote() + "].");
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
