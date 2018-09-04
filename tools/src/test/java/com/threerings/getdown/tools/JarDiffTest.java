//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2016 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.tools;

import java.io.IOException;
import java.io.StringWriter;

import org.junit.Test;

import static com.threerings.getdown.tools.JarDiff.writeEscapedString;
import static org.junit.Assert.assertEquals;

/**
 * Tests {@link JarDiff}.
 */
public class JarDiffTest {

    @Test
    public void testWriteEscapedString () throws IOException
    {
        assertEquals("abc", writeEscapedString(new StringWriter(), "abc").toString());
        assertEquals("abc\\ xyz", writeEscapedString(new StringWriter(), "abc xyz").toString());
        assertEquals("\\ xyz", writeEscapedString(new StringWriter(), " xyz").toString());
        assertEquals("abc\\ ", writeEscapedString(new StringWriter(), "abc ").toString());
        assertEquals("\\ ", writeEscapedString(new StringWriter(), " ").toString());
        assertEquals("", writeEscapedString(new StringWriter(), "").toString());
    }

}
