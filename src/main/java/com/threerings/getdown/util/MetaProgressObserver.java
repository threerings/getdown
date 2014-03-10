//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2014 Three Rings Design, Inc.
// https://raw.github.com/threerings/getdown/master/LICENSE

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
        // add the previous size
        _accum += (_elementSize * 100);
        // then set the new one
        _elementSize = elementSize;
    }

    // documentation inherited from interface
    public void progress (int percent)
    {
        if (_totalSize > 0) {
            _target.progress((int)((_accum + (percent * _elementSize)) / _totalSize));
        }
    }

    protected ProgressObserver _target;
    protected long _totalSize, _accum, _elementSize;
}
