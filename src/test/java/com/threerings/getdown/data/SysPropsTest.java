//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2016 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.data;

import org.junit.*;
import static org.junit.Assert.*;

public class SysPropsTest {

    @After public void clearProps () {
        System.clearProperty("appbase_domain");
        System.clearProperty("appbase_override");
    }

    public static String[] APPBASES = {
        "http://foobar.com/myapp",
        "https://foobar.com/myapp",
        "http://foobar.com:8080/myapp",
        "https://foobar.com:8080/myapp"
    };

    @Test public void testAppbaseDomain () {
        System.setProperty("appbase_domain", "https://barbaz.com");
        for (String appbase : APPBASES) {
            assertEquals("https://barbaz.com/myapp", SysProps.overrideAppbase(appbase));
        }
        System.setProperty("appbase_domain", "http://barbaz.com");
        for (String appbase : APPBASES) {
            assertEquals("http://barbaz.com/myapp", SysProps.overrideAppbase(appbase));
        }
    }

    @Test public void testAppbaseOverride () {
        System.setProperty("appbase_override", "https://barbaz.com/newapp");
        for (String appbase : APPBASES) {
            assertEquals("https://barbaz.com/newapp", SysProps.overrideAppbase(appbase));
        }
    }
}
