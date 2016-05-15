//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2016 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.data;

import org.junit.*;

public class SysPropsTest {

  @Test public void testParseJavaVersion () {
    long vers = SysProps.parseJavaVersion("java.version", "(\\d+)\\.(\\d+)\\.(\\d+)(_\\d+)?");
    assert(vers > 1060000);

    long runVers = SysProps.parseJavaVersion("java.runtime.version",
                                             "(\\d+)\\.(\\d+)\\.(\\d+)(_\\d+)?(-b\\d+)?");
    assert(runVers > 106000000);
  }
}
