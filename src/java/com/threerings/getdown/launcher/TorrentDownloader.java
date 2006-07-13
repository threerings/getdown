package com.threerings.getdown.launcher;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import org.klomp.snark.Snark;
import org.klomp.snark.SnarkShutdown;

import com.threerings.getdown.Log;
import com.threerings.getdown.data.Resource;

public class TorrentDownloader extends Downloader
{
    public TorrentDownloader (List<Resource> resources, Observer obs)
    {
        super(resources, obs);
        Log.info("Using bittorrent to fetch files");
        for (Resource resource : resources) {
            String url = resource.getRemote().toString() + ".torrent";
            Snark snark = new Snark(url, null, -1, null, null);
            snark.command_interpreter = false;
            _torrentmap.put(resource, snark);
        }
    }

    @Override
    protected long checkSize(Resource rsrc)
        throws IOException
    {
        Snark snark = _torrentmap.get(rsrc);
        long length = -1;
        try {
            snark.setupNetwork();
            length = snark.meta.getTotalLength();
        } catch (Exception e) {
            // Unfortunately, the snark library does System.exit(-1) right now
            // instead of properly passing the exception up the chain.
            Log.warning("Bittorrent failed, falling back to HTTP");
            snark.shutdown();
            _fallback = new HTTPDownloader(_resources, _obs);
            length = _fallback.checkSize(rsrc);
        }
        return length;
    }

    @Override
    protected void doDownload(Resource rsrc)
        throws IOException
    {
        if (_fallback != null) {
            _fallback.doDownload(rsrc);
            return;
        }
        Snark snark = _torrentmap.get(rsrc);
        snark.collectPieces();
        SnarkShutdown snarkhook = new SnarkShutdown(snark.storage,
            snark.coordinator, snark.acceptor, snark.trackerclient, snark);
        Runtime.getRuntime().addShutdownHook(snarkhook);
        while (_currentSize != snark.meta.getTotalLength()) {
            long now = System.currentTimeMillis();
            if ((now - _lastUpdate) >= UPDATE_DELAY) {
                _currentSize = snark.coordinator.getDownloaded();
                if (_currentSize < SIZE_THRESHOLD &&
                    (now - _start) >= TIME_THRESHOLD) {
                    // The download isn't going as planned, abort;
                    snark.shutdown();
                    _fallback = new HTTPDownloader(_resources, _obs);
                    _fallback.doDownload(rsrc);   
                    return;
                }
            }
            updateObserver();
        }
        snark.shutdown();
    }

    protected HashMap<Resource, Snark> _torrentmap =
        new HashMap<Resource, Snark>();
    protected HTTPDownloader _fallback = null;
    protected static final long TIME_THRESHOLD = 60 * 1000l;
    protected static final long SIZE_THRESHOLD = 4000l;
}
