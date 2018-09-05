//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2016 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Tests {@link Color}.
 */
public class ColorTest
{
    @Test
    public void testRgbConstructor ()
    {
        Color color1 = new Color(0, 0, 0);
        assertEquals(0, color1.red);
        assertEquals(0, color1.green);
        assertEquals(0, color1.blue);
        assertEquals(255, color1.alpha);
        assertEquals(0xFF000000, color1.rgba());
        assertEquals(0, color1.brightness(), 0.0000001);
        assertEquals(Color.BLACK, color1);

        Color color2 = new Color(255, 255, 255);
        assertEquals(255, color2.red);
        assertEquals(255, color2.green);
        assertEquals(255, color2.blue);
        assertEquals(255, color2.alpha);
        assertEquals(0xFFFFFFFF, color2.rgba());
        assertEquals(1, color2.brightness(), 0.0000001);
        assertEquals(Color.WHITE, color2);

        Color color3 = new Color(1, 2, 3);
        assertEquals(1, color3.red);
        assertEquals(2, color3.green);
        assertEquals(3, color3.blue);
        assertEquals(255, color3.alpha);
        assertEquals(0xFF010203, color3.rgba());
        assertEquals(0.0117647, color3.brightness(), 0.0000001);

        Color color4 = new Color(-100, 300, 200);
        assertEquals(0, color4.red);
        assertEquals(255, color4.green);
        assertEquals(200, color4.blue);
        assertEquals(255, color4.alpha);
        assertEquals(0xFF00FFC8, color4.rgba());
        assertEquals(1, color4.brightness(), 0.0000001);
    }

    @Test
    public void testRgbaConstructor ()
    {
        Color color1 = new Color(0, false);
        assertEquals(0, color1.red);
        assertEquals(0, color1.green);
        assertEquals(0, color1.blue);
        assertEquals(255, color1.alpha);
        assertEquals(0xFF000000, color1.rgba());
        assertEquals(0, color1.brightness(), 0.0000001);
        assertEquals(Color.BLACK, color1);

        Color color2 = new Color(0, true);
        assertEquals(0, color2.red);
        assertEquals(0, color2.green);
        assertEquals(0, color2.blue);
        assertEquals(0, color2.alpha);
        assertEquals(0, color2.rgba());
        assertEquals(0, color2.brightness(), 0.0000001);
        assertFalse(Color.BLACK.equals(color2));

        Color color3 = new Color(0x00FFFFFF, false);
        assertEquals(255, color3.red);
        assertEquals(255, color3.green);
        assertEquals(255, color3.blue);
        assertEquals(255, color3.alpha);
        assertEquals(0xFFFFFFFF, color3.rgba());
        assertEquals(1, color3.brightness(), 0.0000001);
        assertEquals(Color.WHITE, color3);

        Color color4 = new Color(0x00FFFFFF, true);
        assertEquals(255, color4.red);
        assertEquals(255, color4.green);
        assertEquals(255, color4.blue);
        assertEquals(0, color4.alpha);
        assertEquals(0x00FFFFFF, color4.rgba());
        assertEquals(1, color4.brightness(), 0.0000001);
        assertFalse(Color.WHITE.equals(color4));

        Color color5 = new Color(0x00010203, false);
        assertEquals(1, color5.red);
        assertEquals(2, color5.green);
        assertEquals(3, color5.blue);
        assertEquals(255, color5.alpha);
        assertEquals(0xFF010203, color5.rgba());
        assertEquals(0.0117647, color5.brightness(), 0.0000001);

        Color color6 = new Color(0x00010203, true);
        assertEquals(1, color6.red);
        assertEquals(2, color6.green);
        assertEquals(3, color6.blue);
        assertEquals(0, color6.alpha);
        assertEquals(0x00010203, color6.rgba());
        assertEquals(0.0117647, color6.brightness(), 0.0000001);
    }
}
