//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2018 Getdown authors
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

    public Rectangle union (Rectangle other) {
        int x1 = Math.min(x, other.x);
        int x2 = Math.max(x + width, other.x + other.width);
        int y1 = Math.min(y, other.y);
        int y2 = Math.max(y + height, other.y + other.height);
        return new Rectangle(x1, y1, x2 - x1, y2 - y1);
    }

    /** {@inheritDoc} */
    public String toString ()
    {
        return getClass().getName() + "[x=" + x + ", y=" + y +
            ", width=" + width + ", height=" + height + "]";
    }
}
