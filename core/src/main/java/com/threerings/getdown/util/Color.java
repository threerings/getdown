//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2018 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.util;

/**
 * Utilities for handling ARGB colors.
 */
public class Color
{
    public final static int CLEAR = 0x00000000;
    public final static int WHITE = 0xFFFFFFFF;
    public final static int BLACK = 0xFF000000;

    public static float brightness (int argb) {
        // TODO: we're ignoring alpha here...
        int red = (argb >> 16) & 0xFF;
        int green = (argb >> 8) & 0xFF;
        int blue = (argb >> 0) & 0xFF;
        int max = Math.max(Math.max(red, green), blue);
        return ((float) max) / 255.0f;
    }

    private Color () {}
}
