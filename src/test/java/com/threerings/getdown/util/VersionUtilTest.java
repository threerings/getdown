//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2016 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.util;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class VersionUtilTest {

    @Test
    public void shouldParseJavaVersion ()
    {
        long version = VersionUtil.parseJavaVersion(
            "(\\d+)\\.(\\d+)\\.(\\d+)(_\\d+)?", "1.8.0_152");
        assertTrue(version > 1060000);
    }

    @Test
    public void shouldParseJavaRuntimeVersion ()
    {
        long version = VersionUtil.parseJavaVersion(
            "(\\d+)\\.(\\d+)\\.(\\d+)(_\\d+)?(-b\\d+)?", "1.8.0_131-b11");
        assertTrue(version > 106000000);
    }
}
