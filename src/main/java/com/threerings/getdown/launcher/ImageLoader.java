//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2013 Three Rings Design, Inc.
// http://code.google.com/p/getdown/source/browse/LICENSE

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
