//
// $Id: ProgressObserver.java,v 1.1 2004/07/14 13:44:49 mdb Exp $

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
