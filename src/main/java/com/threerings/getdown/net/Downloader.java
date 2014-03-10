//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2014 Three Rings Design, Inc.
// https://raw.github.com/threerings/getdown/master/LICENSE

package com.threerings.getdown.net;

import java.io.File;
import java.io.IOException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.threerings.getdown.data.Resource;

import static com.threerings.getdown.Log.log;

/**
 * Handles the download of a collection of files, first issuing HTTP head requests to obtain size
 * information and then downloading the files individually, reporting progress back via a callback
 * interface.
 */
public abstract class Downloader extends Thread
{
    /**
     * An interface used to communicate status back to an external entity.  <em>Note:</em> these
     * methods are all called on the download thread, so implementors must take care to only
     * execute thread-safe code or simply pass a message to the AWT thread, for example.
     */
    public interface Observer
    {
        /**
         * Called before the downloader begins the series of HTTP head requests to determine the
         * size of the files it needs to download.
         */
        public void resolvingDownloads ();

        /**
         * Called to inform the observer of ongoing progress toward completion of the overall
         * downloading task. The caller is guaranteed to get at least one call reporting 100%
         * completion.
         *
         * @param percent the percent completion, in terms of total file size, of the downloads.
         * @param remaining the estimated download time remaining in seconds, or <code>-1</code> if
         * the time can not yet be determined.
         *
         * @return true if the download should continue, false if it should be aborted.
         */
        public boolean downloadProgress (int percent, long remaining);

        /**
         * Called if a failure occurs while checking for an update or downloading a file.
         *
         * @param rsrc the resource that was being downloaded when the error occurred, or
         * <code>null</code> if the failure occurred while resolving downloads.
         * @param e the exception detailing the failure.
         */
        public void downloadFailed (Resource rsrc, Exception e);
    }

    /**
     * Creates a downloader that will download the supplied list of resources and communicate with
     * the specified observer. The {@link #download} method must be called on the downloader to
     * initiate the download process.
     */
    public Downloader (List<Resource> resources, Observer obs)
    {
        super("Downloader");
        _resources = resources;
        _obs = obs;
    }

    /**
     * This method is invoked as the downloader thread and performs the actual downloading.
     */
    @Override
    public void run ()
    {
        download();
    }

    /**
     * Start downloading the resources in this downloader.
     *
     * @return true if the download completed or failed for unexpected reasons (in which case the
     * observer will have been notified), false if it was aborted by the observer.
     */
    public boolean download ()
    {
        Resource current = null;
        try {
            // let the observer know that we're computing download size
            if (_obs != null) {
                _obs.resolvingDownloads();
            }

            // first compute the total size of our download
            for (Resource resource : _resources) {
                discoverSize(resource);
            }

            long totalSize = sum(_sizes.values());
            log.info("Downloading " + totalSize + " bytes...");

            // make a note of the time at which we started the download
            _start = System.currentTimeMillis();

            // now actually download the files
            for (Resource resource : _resources) {
                download(resource);
            }

            // finally report our download completion if we did not already do so when downloading
            // our final resource
            if (_obs != null && !_complete) {
                if (!_obs.downloadProgress(100, 0)) {
                    return false;
                }
            }

        } catch (DownloadAbortedException e) {
            return false;

        } catch (Exception e) {
            if (_obs != null) {
                _obs.downloadFailed(current, e);
            } else {
                log.warning("Observer failed.", e);
            }
        }
        return true;
    }

    /**
     * Notes the amount of data needed to download the given resource..
     */
    protected void discoverSize (Resource rsrc)
        throws IOException
    {
        _sizes.put(rsrc, Math.max(checkSize(rsrc), 0L));
    }

    /**
     * Performs the protocol-specific portion of checking download size.
     */
    protected abstract long checkSize (Resource rsrc) throws IOException;

    /**
     * Downloads the specified resource from its remote location to its local location.
     */
    protected void download (Resource rsrc)
        throws IOException
    {
        // make sure the resource's target directory exists
        File parent = new File(rsrc.getLocal().getParent());
        if (!parent.exists()) {
            if (!parent.mkdirs()) {
                log.warning("Failed to create target directory for resource '" + rsrc + "'. " +
                            "Download will certainly fail.");
            }
        }
        doDownload(rsrc);
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
     * updated sizes here we can recover from receiving bogus information in the earlier {@link
     * #checkSize} phase.
     */
    protected void updateObserver (Resource rsrc, long currentSize, long actualSize)
        throws IOException
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

            // make sure we only report 100% exactly once
            if (pctdone < 100 || !_complete) {
                _complete = (pctdone == 100);
                if (!_obs.downloadProgress(pctdone, remaining)) {
                    throw new DownloadAbortedException();
                }
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

    /**
     * Accomplishes the copying of the resource from remote location to local location using
     * protocol-specific code
     */
    protected abstract void doDownload (Resource rsrc) throws IOException;

    /** The list of resources to be downloaded. */
    protected List<Resource> _resources;

    /** The reported sizes of our resources. */
    protected Map<Resource, Long> _sizes = new HashMap<Resource, Long>();

    /** The bytes downloaded for each resource. */
    protected Map<Resource, Long> _downloaded = new HashMap<Resource, Long>();

    /** The observer with whom we are communicating. */
    protected Observer _obs;

    /** Used while downloading. */
    protected byte[] _buffer = new byte[4096];

    /** The time at which the file transfer began. */
    protected long _start;

    /** The current transfer rate in bytes per second. */
    protected long _bytesPerSecond;

    /** The time at which the last progress update was posted to the progress observer. */
    protected long _lastUpdate;

    /** Whether the download has completed and the progress observer notified. */
    protected boolean _complete;

    /** The delay in milliseconds between notifying progress observers of file download
     * progress. */
    protected static final long UPDATE_DELAY = 500L;
}
