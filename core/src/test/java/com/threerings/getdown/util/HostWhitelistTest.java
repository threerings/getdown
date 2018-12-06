//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2018 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.util;

import static org.junit.Assert.assertEquals;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/**
 * Tests {@link HostWhitelist}.
 */
public class HostWhitelistTest
{
    @Test
    public void testVerify () throws MalformedURLException
    {
        checkCanVerify("foo.com", "http://foo.com", true);
        checkCanVerify("foo.com", "http://foo.com/", true);
        checkCanVerify("foo.com", "http://foo.com/x/y/z", true);
        checkCanVerify("foo.com", "http://www.foo.com", false);
        checkCanVerify("foo.com", "http://www.foo.com/", false);
        checkCanVerify("foo.com", "http://www.foo.com/x/y/z", false);
        checkCanVerify("foo.com", "http://a.b.foo.com", false);
        checkCanVerify("foo.com", "http://a.b.foo.com/", false);
        checkCanVerify("foo.com", "http://a.b.foo.com/x/y/z", false);
        checkCanVerify("foo.com", "http://oo.com", false);
        checkCanVerify("foo.com", "http://f.oo.com", false);
        checkCanVerify("foo.com", "http://a.f.oo.com", false);

        checkCanVerify("*.foo.com", "http://foo.com", false);
        checkCanVerify("*.foo.com", "http://foo.com/", false);
        checkCanVerify("*.foo.com", "http://foo.com/x/y/z", false);
        checkCanVerify("*.foo.com", "http://www.foo.com", true);
        checkCanVerify("*.foo.com", "http://www.foo.com/", true);
        checkCanVerify("*.foo.com", "http://www.foo.com/x/y/z", true);
        checkCanVerify("*.foo.com", "http://a.b.foo.com", true);
        checkCanVerify("*.foo.com", "http://a.b.foo.com/", true);
        checkCanVerify("*.foo.com", "http://a.b.foo.com/x/y/z", true);
        checkCanVerify("*.foo.com", "http://oo.com", false);
        checkCanVerify("*.foo.com", "http://f.oo.com", false);
        checkCanVerify("*.foo.com", "http://a.f.oo.com", false);

        checkCanVerify("*.com", "http://foo.com", true);
        checkCanVerify("*.com", "http://foo.com/", true);
        checkCanVerify("*.com", "http://foo.com/x/y/z", true);
        checkCanVerify("*.com", "http://www.foo.com", true);
        checkCanVerify("*.com", "http://www.foo.com/", true);
        checkCanVerify("*.com", "http://www.foo.com/x/y/z", true);
        checkCanVerify("*.com", "http://a.b.foo.com", true);
        checkCanVerify("*.com", "http://a.b.foo.com/", true);
        checkCanVerify("*.com", "http://a.b.foo.com/x/y/z", true);
        checkCanVerify("*.com", "http://oo.com", true);
        checkCanVerify("*.com", "http://f.oo.com", true);
        checkCanVerify("*.com", "http://a.f.oo.com", true);

        checkCanVerify("*.net", "http://foo.com", false);
        checkCanVerify("*.net", "http://foo.com/", false);
        checkCanVerify("*.net", "http://foo.com/x/y/z", false);
        checkCanVerify("*.net", "http://www.foo.com", false);
        checkCanVerify("*.net", "http://www.foo.com/", false);
        checkCanVerify("*.net", "http://www.foo.com/x/y/z", false);
        checkCanVerify("*.net", "http://a.b.foo.com", false);
        checkCanVerify("*.net", "http://a.b.foo.com/", false);
        checkCanVerify("*.net", "http://a.b.foo.com/x/y/z", false);
        checkCanVerify("*.net", "http://oo.com", false);
        checkCanVerify("*.net", "http://f.oo.com", false);
        checkCanVerify("*.net", "http://a.f.oo.com", false);

        checkCanVerify("www.*.com", "http://foo.com", false);
        checkCanVerify("www.*.com", "http://foo.com/", false);
        checkCanVerify("www.*.com", "http://foo.com/x/y/z", false);
        checkCanVerify("www.*.com", "http://www.foo.com", true);
        checkCanVerify("www.*.com", "http://www.foo.com/", true);
        checkCanVerify("www.*.com", "http://www.foo.com/x/y/z", true);
        checkCanVerify("www.*.com", "http://a.b.foo.com", false);
        checkCanVerify("www.*.com", "http://a.b.foo.com/", false);
        checkCanVerify("www.*.com", "http://a.b.foo.com/x/y/z", false);
        checkCanVerify("www.*.com", "http://oo.com", false);
        checkCanVerify("www.*.com", "http://f.oo.com", false);
        checkCanVerify("www.*.com", "http://a.f.oo.com", false);
        checkCanVerify("www.*.com", "http://www.a.f.oo.com", true);

        checkCanVerify("foo.*", "http://foo.com", true);
        checkCanVerify("foo.*", "http://foo.com/", true);
        checkCanVerify("foo.*", "http://foo.com/x/y/z", true);
        checkCanVerify("foo.*", "http://www.foo.com", false);
        checkCanVerify("foo.*", "http://www.foo.com/", false);
        checkCanVerify("foo.*", "http://www.foo.com/x/y/z", false);
        checkCanVerify("foo.*", "http://a.b.foo.com", false);
        checkCanVerify("foo.*", "http://a.b.foo.com/", false);
        checkCanVerify("foo.*", "http://a.b.foo.com/x/y/z", false);
        checkCanVerify("foo.*", "http://oo.com", false);
        checkCanVerify("foo.*", "http://f.oo.com", false);
        checkCanVerify("foo.*", "http://a.f.oo.com", false);

        checkCanVerify("*.foo.*", "http://foo.com", false);
        checkCanVerify("*.foo.*", "http://foo.com/", false);
        checkCanVerify("*.foo.*", "http://foo.com/x/y/z", false);
        checkCanVerify("*.foo.*", "http://www.foo.com", true);
        checkCanVerify("*.foo.*", "http://www.foo.com/", true);
        checkCanVerify("*.foo.*", "http://www.foo.com/x/y/z", true);
        checkCanVerify("*.foo.*", "http://a.b.foo.com", true);
        checkCanVerify("*.foo.*", "http://a.b.foo.com/", true);
        checkCanVerify("*.foo.*", "http://a.b.foo.com/x/y/z", true);
        checkCanVerify("*.foo.*", "http://oo.com", false);
        checkCanVerify("*.foo.*", "http://f.oo.com", false);
        checkCanVerify("*.foo.*", "http://a.f.oo.com", false);

        checkCanVerify("127.0.0.1", "http://127.0.0.1", true);
        checkCanVerify("127.0.0.1", "http://127.0.0.1/", true);
        checkCanVerify("127.0.0.1", "http://127.0.0.1/x/y/z", true);
        checkCanVerify("*.0.0.1", "http://127.0.0.1/abc", true);
        checkCanVerify("127.*.0.1", "http://127.0.0.1/abc", true);
        checkCanVerify("127.0.*.1", "http://127.0.0.1/abc", true);
        checkCanVerify("127.0.0.*", "http://127.0.0.1/abc", true);
        checkCanVerify("127.*.1", "http://127.0.0.1/abc", true);
        checkCanVerify("*.0.1", "http://127.0.0.1/abc", true);
        checkCanVerify("127.0.*", "http://127.0.0.1/abc", true);
        checkCanVerify("*", "http://127.0.0.1/abc", true);
        checkCanVerify("127.0.0.2", "http://127.0.0.1", false);
        checkCanVerify("127.0.2.1", "http://127.0.0.1", false);
        checkCanVerify("127.2.0.1", "http://127.0.0.1", false);
        checkCanVerify("222.0.0.1", "http://127.0.0.1", false);

        checkCanVerify("", "http://foo.com", true);
        checkCanVerify("", "http://aaa.bbb.net/xyz", true);
        checkCanVerify("", "https://127.0.0.1/abc", true);

        checkCanVerify("aaa.bbb.com,xxx.yyy.com, *.jjj.net", "http://aaa.bbb.com/m", true);
        checkCanVerify("aaa.bbb.com, xxx.yyy.com,*.jjj.net", "http://xxx.yyy.com/n", true);
        checkCanVerify("aaa.bbb.com,xxx.yyy.com, *.jjj.net", "http://www.jjj.net/o", true);
    }

    private static void checkCanVerify (String whitelist, String url, boolean expectedToPass)
        throws MalformedURLException
    {
        List<String> w = Arrays.asList(StringUtil.parseStringArray(whitelist));
        URL u = new URL(url);
        boolean passed;

        try {
            HostWhitelist.verify(w, u);
            passed = true;
        } catch (MalformedURLException e) {
            passed = false;
        }

        assertEquals("with whitelist '" + whitelist + "' and URL '" + url + "'",
            expectedToPass, passed);
    }
}
