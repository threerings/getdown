//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2016 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.util;

/**
 * An immutable rectangle.
 */
public class Rectangle
{
    public final int x;
    public final int y;
    public final int width;
    public final int height;

    public Rectangle (int x, int y, int width, int height)
    {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /** {@inheritDoc} */
    public String toString ()
    {
        return getClass().getName() + "[x=" + x + ", y=" + y + ", width=" + width + ", height=" + height + "]";
    }
}
