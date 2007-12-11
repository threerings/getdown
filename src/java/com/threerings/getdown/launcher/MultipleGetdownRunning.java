package com.threerings.getdown.launcher;

import java.io.IOException;

/**
 * Thrown when it's detected that multiple instances of the same getdown installer are running.
 */
public class MultipleGetdownRunning extends IOException
{
    public MultipleGetdownRunning ()
    {
        super("m.another_getdown_running");
    }

}
