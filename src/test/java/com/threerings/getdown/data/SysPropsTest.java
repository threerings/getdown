//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2016 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.data;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class SysPropsTest
{
    @Test
    public void shouldParseJavaVersion ()
    {
        long version = SysProps.parseJavaVersion(
            "java.version", "(\\d+)\\.(\\d+)\\.(\\d+)(_\\d+)?");
        assertTrue(version > 1060000);
    }

    @Test
    public void shouldParseJavaRuntimeVersion ()
    {
        long version = SysProps.parseJavaVersion(
            "java.runtime.version", "(\\d+)\\.(\\d+)\\.(\\d+)(_\\d+)?(-b\\d+)?");
        assertTrue(version > 106000000);
    }
}
