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

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import org.klomp.snark.Snark;
import org.klomp.snark.SnarkShutdown;

import com.threerings.getdown.data.Resource;

import static com.threerings.getdown.Log.log;

/**
 * Implements downloading data using BitTorrent
 */
public class TorrentDownloader extends Downloader
{
    public TorrentDownloader (List<Resource> resources, Observer obs)
    {
        super(resources, obs);
        log.info("Using bittorrent to fetch files");
        for (Resource resource : resources) {
            String url = resource.getRemote().toString() + ".torrent";
            Snark snark = new Snark(url, null, -1, null, null);
            SnarkShutdown snarkStopper = new SnarkShutdown(snark, null);
            Runtime.getRuntime().addShutdownHook(snarkStopper);
            _torrentmap.put(resource, snark);
            _stoppermap.put(resource, snarkStopper);
            if (resource.getPath().equals("full")) {
                _metaDownload = true;
                break;
            }
        }
    }

    // documentation inherited
    protected long checkSize(Resource rsrc)
        throws IOException
    {
        if (_metaDownload && !rsrc.getPath().equals("full")) {
            return 0;
        }
        if (_fallback != null) {
            return _fallback.checkSize(rsrc);
        }
        Snark snark = _torrentmap.get(rsrc);
        long length = -1;
        try {
            snark.setupNetwork();
            length = snark.meta.getTotalLength();
        } catch (IOException ioe) {
            log.warning("Bittorrent failed, falling back to HTTP");
            SnarkShutdown stopper = _stoppermap.get(rsrc);
            stopper.run();
            Runtime.getRuntime().removeShutdownHook(stopper);
            fallback();
            if (_metaDownload && rsrc.getPath().equals("full")) {
                length = 0;
            } else {
                length = _fallback.checkSize(rsrc);
            }
            _metaDownload = false;
        }
        return length;
    }

    // documentation inherited
    protected void doDownload(Resource rsrc)
        throws IOException
    {
        if (_metaDownload && !rsrc.getPath().equals("full")) {
            return;
        }
        if (_fallback != null) {
            if (rsrc.getPath().equals("full")) {
                return;
            } else {
                _fallback.doDownload(rsrc);
                return;
            }
        }
        Snark snark = _torrentmap.get(rsrc);
        SnarkShutdown snarkStopper = _stoppermap.get(rsrc);
        snark.collectPieces();
        // Override the start time, since Snark allocates storage prior to
        // doing any downloading
        _start = System.currentTimeMillis();
        while (!snark.coordinator.completed()) {
            long now = System.currentTimeMillis();
            if ((now - _lastUpdate) >= UPDATE_DELAY) {
                _currentSize = snark.coordinator.getDownloaded();
                if ((_currentSize < SIZE_THRESHOLD &&
                        (now - _start) >= TIME_THRESHOLD)) {
                    log.info("Torrenting too slow, falling back to HTTP.");
                    // The download isn't going as planned, abort;
                    snarkStopper.run();
                    Runtime.getRuntime().removeShutdownHook(snarkStopper);
                    snarkStopper = null;
                    _stoppermap.remove(rsrc);
                    if (_metaDownload) {
                        _metaDownload = false;
                    }
                    fallback();
                    return;
                }
            }
            updateObserver();
        }
        // Manually set completion, just to be extra-safe.
        _currentSize = _totalSize;
        updateObserver();

        if (snarkStopper != null) {
            snarkStopper.run();
            Runtime.getRuntime().removeShutdownHook(snarkStopper);
            _stoppermap.remove(rsrc);
        }
    }

    /**
     * If torrent downloading either bugs out or is too slow, switch to a
     * different method by creating the fallback downloader.
     */
    protected void fallback ()
    {
        _fallback = new HTTPDownloader(_resources, _obs, _totalSize);
    }

    /** Keeps a mapping of resource names to torrent downloaders */
    protected HashMap<Resource, Snark> _torrentmap =
        new HashMap<Resource, Snark>();

    /** Keeps a mapping of resource names to torrent stoppers */
    protected HashMap<Resource, SnarkShutdown> _stoppermap =
        new HashMap<Resource, SnarkShutdown>();

    /** If we fail, revert to using this HTTP download transport */
    protected HTTPDownloader _fallback = null;

    /** The length of time before we check for adequate progress*/
    protected static final long TIME_THRESHOLD = 60 * 1000l;

    /**
     * Whether we are downloading an artificially-generated metafile
     * representing all of the {@link Resource}s at the end of the file.
     */
    protected boolean _metaDownload = false;

    /**
     * The minimum amount of data that must be downloaded within the
     * initial period in order to continue using BitTorrent
     */
    protected static final long SIZE_THRESHOLD = 4000l;
}
