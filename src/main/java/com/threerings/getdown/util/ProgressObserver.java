//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2013 Three Rings Design, Inc.
// http://code.google.com/p/getdown/source/browse/LICENSE

package com.threerings.getdown.util;

/**
 * Used to communicate progress.
 */
public interface ProgressObserver
{
    /**
     * Informs the observer that we have completed the specified
     * percentage of the process.
     */
    public void progress (int percent);
}
