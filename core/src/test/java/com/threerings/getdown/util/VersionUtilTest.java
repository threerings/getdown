//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2018 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class VersionUtilTest {

    @Test
    public void shouldParseJavaVersion ()
    {
        long version = VersionUtil.parseJavaVersion(
            "(\\d+)(?:\\.(\\d+)(?:\\.(\\d+)(_\\d+)?)?)?", "1.8.0_152");
        assertEquals(1_080_152, version);
    }

    @Test
    public void shouldParseJavaVersion8 ()
    {
        long version = VersionUtil.parseJavaVersion(
            "(\\d+)(?:\\.(\\d+)(?:\\.(\\d+)(_\\d+)?)?)?", "1.8");
        assertEquals(1_080_000, version);
    }

    @Test
    public void shouldParseJavaVersion9 ()
    {
        long version = VersionUtil.parseJavaVersion(
            "(\\d+)(?:\\.(\\d+)(?:\\.(\\d+)(_\\d+)?)?)?", "9");
        assertEquals(9_000_000, version);
    }

    @Test
    public void shouldParseJavaVersion10 ()
    {
        long version = VersionUtil.parseJavaVersion(
            "(\\d+)(?:\\.(\\d+)(?:\\.(\\d+)(_\\d+)?)?)?", "10");
        assertEquals(10_000_000, version);
    }

    @Test
    public void shouldParseJavaRuntimeVersion ()
    {
        long version = VersionUtil.parseJavaVersion(
            "(\\d+)\\.(\\d+)\\.(\\d+)(_\\d+)?(-b\\d+)?", "1.8.0_131-b11");
        assertEquals(108_013_111, version);
    }
}
