//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2016 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.util;

/**
 * An immutable color.
 */
public class Color
{
    public final static Color WHITE = new Color(255, 255, 255);
    public final static Color BLACK = new Color(0, 0, 0);

    public final int red;
    public final int green;
    public final int blue;
    public final int alpha;

    public Color (int r, int g, int b)
    {
        this.red = Math.min(Math.max(r, 0), 255);
        this.green = Math.min(Math.max(g, 0), 255);
        this.blue = Math.min(Math.max(b, 0), 255);
        this.alpha = 255;
    }

    public Color (int rgba, boolean hasAlpha)
    {
        this.red = (rgba >> 16) & 0xFF;
        this.green = (rgba >> 8) & 0xFF;
        this.blue = (rgba >> 0) & 0xFF;

        if (hasAlpha) {
            this.alpha = (rgba >> 24) & 0xFF;
        } else {
            this.alpha = 255;
        }
    }

    /**
     * Returns the combined RBGA value for this color.
     */
    public int rgba ()
    {
        return ((alpha & 0xFF) << 24) |
               ((red & 0xFF) << 16) |
               ((green & 0xFF) << 8)  |
               ((blue & 0xFF) << 0);
    }

    /**
     * Returns the brightness of this color, per the standard HSB model.
     */
    public float brightness ()
    {
        int max = Math.max(Math.max(red, green), blue);
        return ((float) max) / 255.0f;
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object other)
    {
        return other instanceof Color && ((Color)other).rgba() == this.rgba();
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode()
    {
        return rgba();
    }

    /** {@inheritDoc} */
    public String toString ()
    {
        return getClass().getName() + "[r=" + red + ", g=" + green + ", b=" + blue + ", a=" + alpha + "]";
    }
}
