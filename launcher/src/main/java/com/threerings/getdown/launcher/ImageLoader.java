//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2016 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.launcher;

import java.awt.Image;

/**
 * Abstracts away the process of loading an image so that it can be done differently in the app and
 * applet.
 */
public interface ImageLoader
{
    /** Loads and returns the image with the supplied path. */
    public Image loadImage (String path);
}
