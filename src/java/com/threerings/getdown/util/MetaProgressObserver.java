//
// $Id: MetaProgressObserver.java,v 1.1 2004/07/14 13:44:49 mdb Exp $

package com.threerings.getdown.util;

/**
 * Accumulates the progress from a number of elements into a single
 * smoothly progressing progress.
 */
public class MetaProgressObserver implements ProgressObserver
{
    public MetaProgressObserver (ProgressObserver target, long totalSize)
    {
        _target = target;
        _totalSize = totalSize;
    }

    public void startElement (long elementSize)
    {
        _currentSize += elementSize;
        _elementSize = elementSize;
    }

    // documentation inherited from interface
    public void progress (int percent)
    {
        if (_target != null  && _elementSize > 0) {
            long position = _currentSize + (100 * percent / _elementSize);
            _target.progress((int)(100 * position / _totalSize));
        }
    }

    protected ProgressObserver _target;
    protected long _totalSize, _currentSize, _elementSize;
}
