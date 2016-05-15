//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2016 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.util;

/**
 * Accumulates the progress from a number of (potentially parallel) elements into a single smoothly
 * progressing progress.
 */
public class ProgressAggregator
{
    public ProgressAggregator (ProgressObserver target, long[] sizes) {
        _target = target;
        _sizes = sizes;
        _progress = new int[sizes.length];
    }

    public ProgressObserver startElement (final int index) {
        return new ProgressObserver() {
            public void progress (int percent) {
                _sizes[index] = percent;
                updateAggProgress();
            }
        };
    }

    protected void updateAggProgress () {
        long totalSize = 0L, currentSize = 0L;
        synchronized (this) {
            for (int ii = 0, ll = _sizes.length; ii < ll; ii++) {
                long size = _sizes[ii];
                totalSize += size;
                currentSize += (int)((size * _progress[ii])/100.0);
            }
        }
        _target.progress((int)(100.0*currentSize / totalSize));
    }

    protected static long sum (long[] sizes) {
        long totalSize = 0L;
        for (long size : sizes) totalSize += size;
        return totalSize;
    }

    protected ProgressObserver _target;
    protected long[] _sizes;
    protected int[] _progress;
}
