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

package com.threerings.getdown.launcher;

import java.io.File;
import java.io.IOException;

import java.util.List;

import com.threerings.getdown.Log;
import com.threerings.getdown.data.Resource;

/**
 * Handles the download of a collection of files, first issuing HTTP head
 * requests to obtain size information and then downloading the files
 * individually, reporting progress back via a callback interface.
 */
public abstract class Downloader extends Thread
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
    public Downloader (List<Resource> resources, Observer obs)
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
            for (Resource resource : _resources) {
                discoverSize(resource);
            }

            Log.info("Downloading " + _totalSize + " bytes...");

            // make a note of the time at which we started the download
            _start = System.currentTimeMillis();

            // now actually download the files
            for (Resource resource : _resources) {
                download(resource);
            }

            // finally report our download completion if we did not
            // already do so when downloading our final resource
            if (_obs != null && !_complete) {
                _obs.downloadProgress(100, 0);
            }

        } catch (Exception e) {
            if (_obs != null) {
                _obs.downloadFailed(current, e);
            } else {
                Log.logStackTrace(e);
            }
        }
    }

    /**
     * Notes the amount of data needed to download the given resource..
     */
    protected void discoverSize (Resource rsrc)
        throws IOException
    {
        // add this resource's size to our total download size
        _totalSize += checkSize(rsrc);
    }

    protected abstract long checkSize (Resource rsrc) throws IOException;

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
        doDownload(rsrc);
    }

    protected void updateObserver ()
    {
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

    /**
     * Accomplishes the copying of the resource from remote location to
     * local location using transport-specific code
     */
    protected abstract void doDownload (Resource rsrc) throws IOException;

    /** The list of resources to be downloaded. */
    protected List<Resource> _resources;

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
    protected static final long UPDATE_DELAY = 500L;
}
