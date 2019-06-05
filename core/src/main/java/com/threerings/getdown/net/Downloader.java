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
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.threerings.getdown.data.Resource;

import static com.threerings.getdown.Log.log;

/**
 * Handles the download of a collection of files, first issuing HTTP head requests to obtain size
 * information and then downloading the files individually, reporting progress back via protected
 * callback methods. <em>Note:</em> these methods are all called arbitrary download threads, so
 * implementors must take care to only execute thread-safe code or simply pass a message to the AWT
 * thread, for example.
 */
public class Downloader
{
    public Downloader (Connector conn)
    {
        _conn = conn;
    }

    /**
     * Start the downloading process.
     * @param resources the resources to download.
     * @param maxConcurrent the maximum number of concurrent downloads allowed.
     * @return true if the download completed, false if it was aborted (via {@link #abort}).
     */
    public boolean download (Collection<Resource> resources, int maxConcurrent)
    {
        // first compute the total size of our download
        resolvingDownloads();
        for (Resource rsrc : resources) {
            try {
                _sizes.put(rsrc, Math.max(checkSize(rsrc), 0L));
            } catch (IOException ioe) {
                downloadFailed(rsrc, ioe);
            }
        }

        long totalSize = sum(_sizes.values());
        log.info("Downloading " + resources.size() + " resources",
                 "totalBytes", totalSize, "maxConcurrent", maxConcurrent);

        // make a note of the time at which we started the download
        _start = System.currentTimeMillis();

        // start the downloads
        ExecutorService exec = Executors.newFixedThreadPool(maxConcurrent);
        for (final Resource rsrc : resources) {
            // make sure the resource's target directory exists
            File parent = new File(rsrc.getLocal().getParent());
            if (!parent.exists() && !parent.mkdirs()) {
                log.warning("Failed to create target directory for resource '" + rsrc + "'.");
            }

            exec.execute(new Runnable() {
                @Override public void run () {
                    try {
                        if (_state != State.ABORTED) {
                            download(rsrc);
                        }
                    } catch (IOException ioe) {
                        _state = State.FAILED;
                        downloadFailed(rsrc, ioe);
                    }
                }
            });
        }
        exec.shutdown();

        // wait for the downloads to complete
        try {
            exec.awaitTermination(10, TimeUnit.DAYS);

            // report download completion if we did not already do so via our final resource
            if (_state == State.DOWNLOADING) {
                downloadProgress(100, 0);
            }

        } catch (InterruptedException ie) {
            exec.shutdownNow();
            downloadFailed(null, ie);
        }

        return _state != State.ABORTED;
    }

    /**
     * Aborts the in-progress download.
     */
    public void abort () {
        _state = State.ABORTED;
    }

    /**
     * Called before the downloader begins the series of HTTP head requests to determine the
     * size of the files it needs to download.
     */
    protected void resolvingDownloads () {}

    /**
     * Reports ongoing progress toward completion of the overall downloading task. One call is
     * guaranteed to be made reporting 100% completion if the download is not aborted and no
     * resources fail.
     *
     * @param percent the percent completion of the complete download process (based on total bytes
     * downloaded versus total byte size of all resources).
     * @param remaining the estimated download time remaining in seconds, or {@code -1} if the time
     * can not yet be determined.
     */
    protected void downloadProgress (int percent, long remaining) {}

    /**
     * Called if a failure occurs while downloading a resource. No progress will be reported after
     * a download fails, but additional download failures may be reported.
     *
     * @param rsrc the resource that failed to download, or null if the download failed due to
     * thread interruption.
     * @param cause the exception detailing the failure.
     */
    protected void downloadFailed (Resource rsrc, Exception cause) {}

    /**
     * Called when a to-be-downloaded resource returns a 404 not found.
     */
    protected void resourceMissing (Resource rsrc) {}

    /**
     * Performs the protocol-specific portion of checking download size.
     */
    protected long checkSize (Resource rsrc) throws IOException {
        URLConnection conn = _conn.open(rsrc.getRemote(), 0, 0);
        try {
            // if we're accessing our data via HTTP, we only need a HEAD request
            if (conn instanceof HttpURLConnection) {
                ((HttpURLConnection)conn).setRequestMethod("HEAD");
            }
            // if we get a satisfactory response code, return a size; ignore errors as we'll report
            // those when we actually attempt to download the resource
            int code = _conn.checkConnectStatus(conn);
            return code == HttpURLConnection.HTTP_OK ? conn.getContentLength() : 0;

        } finally {
            // let it be known that we're done with this connection
            conn.getInputStream().close();
        }
    }

    /**
     * Periodically called by the protocol-specific downloaders to update their progress. This
     * should be called at least once for each resource to be downloaded, with the total downloaded
     * size for that resource. It can also be called periodically along the way for each resource
     * to communicate incremental progress.
     *
     * @param rsrc the resource currently being downloaded.
     * @param currentSize the number of bytes currently downloaded for said resource.
     * @param actualSize the size reported for this resource now that we're actually downloading
     * it. Some web servers lie about Content-length when doing a HEAD request, so by reporting
     * updated sizes here we can recover from receiving bogus information in the earlier
     * {@link #checkSize} phase.
     */
    protected synchronized void reportProgress (Resource rsrc, long currentSize, long actualSize)
    {
        // update the actual size for this resource (but don't let it shrink)
        _sizes.put(rsrc, actualSize = Math.max(actualSize, _sizes.get(rsrc)));

        // update the current downloaded size for said resource; don't allow the downloaded bytes
        // to exceed the original claimed size of the resource, otherwise our progress will get
        // booched and we'll end up back on the Daily WTF: http://tinyurl.com/29wt4oq
        _downloaded.put(rsrc, Math.min(actualSize, currentSize));

        // notify the observer if it's been sufficiently long since our last notification
        long now = System.currentTimeMillis();
        if ((now - _lastUpdate) >= UPDATE_DELAY) {
            _lastUpdate = now;

            // total up our current and total bytes
            long downloaded = sum(_downloaded.values());
            long totalSize = sum(_sizes.values());

            // compute our bytes per second
            long secs = (now - _start) / 1000L;
            long bps = (secs == 0) ? 0 : (downloaded / secs);

            // compute our percentage completion
            int pctdone = (totalSize == 0) ? 0 : (int)((downloaded * 100f) / totalSize);

            // estimate our time remaining
            long remaining = (bps <= 0 || totalSize == 0) ? -1 : (totalSize - downloaded) / bps;

            // if we're complete or failed, when we don't want to report again
            if (_state == State.DOWNLOADING) {
                if (pctdone == 100) _state = State.COMPLETE;
                downloadProgress(pctdone, remaining);
            }
        }
    }

    /**
     * Sums the supplied values.
     */
    protected static long sum (Iterable<Long> values)
    {
        long acc = 0L;
        for (Long value : values) {
            acc += value;
        }
        return acc;
    }

    protected enum State { DOWNLOADING, COMPLETE, FAILED, ABORTED }

    /**
     * Accomplishes the copying of the resource from remote location to local location using
     * protocol-specific code. This method should periodically check whether {@code _state} is set
     * to aborted and abort any in-progress download if so.
     */
    protected void download (Resource rsrc) throws IOException {
        URLConnection conn = _conn.open(rsrc.getRemote(), 0, 0);
        // make sure we got a satisfactory response code
        int code = _conn.checkConnectStatus(conn);
        if (code == HttpURLConnection.HTTP_NOT_FOUND) {
            resourceMissing(rsrc);
        } else if (code != HttpURLConnection.HTTP_OK) {
            throw new IOException(
                "Resource returned HTTP error " + rsrc.getRemote() + " [code=" + code + "]");
        }

        // TODO: make FileChannel download impl (below) robust and allow apps to opt-into it via a
        // system property
        if (true) {
            // download the resource from the specified URL
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
            try (ReadableByteChannel rbc = Channels.newChannel(conn.getInputStream());
                 FileOutputStream fos = new FileOutputStream(localNew)) {
                // TODO: more work is needed here, transferFrom can fail to transfer the entire
                // file, in which case it's not clear what we're supposed to do.. call it again?
                // will it repeatedly fail?
                fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
                reportProgress(rsrc, localNew.length(), localNew.length());
            }
        }
    }

    protected final Connector _conn;

    /** The reported sizes of our resources. */
    protected final Map<Resource, Long> _sizes = new HashMap<>();

    /** The bytes downloaded for each resource. */
    protected final Map<Resource, Long> _downloaded = new HashMap<>();

    /** The time at which the file transfer began. */
    protected long _start;

    /** The current transfer rate in bytes per second. */
    protected long _bytesPerSecond;

    /** The time at which the last progress update was posted to the progress observer. */
    protected long _lastUpdate;

    /** A wee state machine to ensure we call our callbacks sanely. */
    protected volatile State _state = State.DOWNLOADING;

    /** The delay in milliseconds between notifying progress observers of file download
      * progress. */
    protected static final long UPDATE_DELAY = 500L;
}
