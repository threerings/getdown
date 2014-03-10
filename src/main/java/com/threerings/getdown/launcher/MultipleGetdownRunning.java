//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2014 Three Rings Design, Inc.
// https://raw.github.com/threerings/getdown/master/LICENSE

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
