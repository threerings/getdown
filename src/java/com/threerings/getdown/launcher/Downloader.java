//
// $Id: Downloader.java,v 1.1 2004/07/06 05:13:36 mdb Exp $

package com.threerings.getdown.launcher;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import java.net.HttpURLConnection;

import java.util.Iterator;
import java.util.List;

import com.samskivert.io.StreamUtil;

import com.threerings.getdown.Log;
import com.threerings.getdown.data.Resource;

/**
 * Handles the download of a collection of files, first issuing HTTP head
 * requests to obtain size information and then downloading the files
 * individually, reporting progress back via a callback interface.
 */
public class Downloader extends Thread
{
    /**
     * An interface used to communicate status back to an external entity.
     * <em>Note:</em> these methods are all called on the download thread,
     * so implementors must take care to only execute thread-safe code or
     * simply pass a message to the AWT thread, for example.
     */
    public interface Observer
    {
        /**
         * Called before the downloader begins the series of HTTP head
         * requests to determine the size of the files it needs to
         * download.
         */
        public void resolvingDownloads ();

        /**
         * Called to inform the observer of ongoing progress toward
         * completion of the overall downloading task.  The caller is
         * guaranteed to get at least one call reporting 100% completion.
         *
         * @param percent the percent completion, in terms of total file
         * size, of the downloads.
         * @param remaining the estimated download time remaining in
         * seconds, or <code>-1</code> if the time can not yet be
         * determined.
         */
        public void downloadProgress (int percent, long remaining);

        /**
         * Called if a failure occurs while checking for an update or
         * downloading a file.
         *
         * @param rsrc the resource that was being downloaded when the
         * error occurred, or <code>null</code> if the failure occurred
         * while resolving downloads.
         * @param e the exception detailing the failure.
         */
        public void downloadFailed (Resource rsrc, Exception e);
    }

    /**
     * Creates a downloader that will download the supplied list of
     * resources and communicate with the specified observer. The {@link
     * #start} method must be called on the downloader to initiate the
     * download process.
     */
    public Downloader (List resources, Observer obs)
    {
        super("Downloader");
        _resources = resources;
        _obs = obs;
    }

    /**
     * This method is invoked as the downloader thread and performs the
     * actual downloading.
     */
    public void run ()
    {
        Resource current = null;
        try {
            // let the observer know that we're computing download size
            if (_obs != null) {
                _obs.resolvingDownloads();
            }

            // first compute the total size of our download
            for (Iterator iter = _resources.iterator(); iter.hasNext(); ) {
                discoverSize((Resource)iter.next());
            }

            // make a note of the time at which we started the download
            _start = System.currentTimeMillis();

            // now actually download the files
            for (Iterator iter = _resources.iterator(); iter.hasNext(); ) {
                current = (Resource)iter.next();
                download(current);
            }

            // finally report our download completion if we did not
            // already do so when downloading our final resource
            if (_obs != null && !_complete) {
                _obs.downloadProgress(100, 0);
            }

        } catch (Exception e) {
            if (_obs != null) {
                _obs.downloadFailed(current, e);
            }
        }
    }

    /**
     * Issues a HEAD request for the specified resource and notes the
     * amount of data we will be downloading to account for it.
     */
    protected void discoverSize (Resource rsrc)
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

        // add this resource's size to our total download size
        _totalSize += ucon.getContentLength();
    }

    /**
     * Downloads the specified resource from its remote location to its
     * local location.
     */
    protected void download (Resource rsrc)
        throws IOException
    {
        // make sure the resource's target directory exists
        File parent = new File(rsrc.getLocal().getParent());
        if (!parent.exists()) {
            if (!parent.mkdirs()) {
                Log.warning("Failed to create target directory for " +
                            "resource '" + rsrc + "'. Download will " +
                            "certainly fail.");
            }
        }

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

                // notify the observer if it's been sufficiently long
                // since our last notification
                long now = System.currentTimeMillis();
                if ((now - _lastUpdate) >= UPDATE_DELAY) {
                    _lastUpdate = now;

                    // compute our bytes per second
                    long secs = (now - _start) / 1000L;
                    long bps = (secs == 0) ? 0 : (_currentSize / secs);

                    // compute our percentage completion
                    int pctdone = (int)(
                        (_currentSize / (float)_totalSize) * 100f);

                    // estimate our time remaining
                    long remaining = (bps <= 0) ? -1 :
                        (_totalSize - _currentSize) / bps;

                    // make sure we only report 100% exactly once
                    if (pctdone < 100 || !_complete) {
                        _complete = (pctdone == 100);
                        _obs.downloadProgress(pctdone, remaining);
                    }
                }
            }

        } finally {
            StreamUtil.close(in);
            StreamUtil.close(out);
        }
    }

    /** The list of resources to be downloaded. */
    protected List _resources;

    /** The observer with whom we are communicating. */
    protected Observer _obs;

    /** Used while downloading. */
    protected byte[] _buffer = new byte[4096];

    /** The total file size in bytes to be transferred. */
    protected long _totalSize;

    /** The file size in bytes transferred thus far. */
    protected long _currentSize;

    /** The time at which the file transfer began. */
    protected long _start;

    /** The current transfer rate in bytes per second. */
    protected long _bytesPerSecond;

    /** The time at which the last progress update was posted to the
     * progress observer. */
    protected long _lastUpdate;

    /** Whether the download has completed and the progress observer
     * notified. */
    protected boolean _complete;

    /** The delay in milliseconds between notifying progress observers of
     * file download progress. */
    protected static final long UPDATE_DELAY = 2500L;
}
