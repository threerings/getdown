//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2014 Three Rings Design, Inc.
// https://raw.github.com/threerings/getdown/master/LICENSE

package com.threerings.getdown.launcher;

import java.awt.Image;

import static com.threerings.getdown.Log.log;

public class RotatingBackgrounds
{

    /**
     * Creates a placeholder if there are no images. Just returns null from getImage every time.
     */
    public RotatingBackgrounds ()
    {
        makeEmpty();
    }

    /** Creates a single image background. */
    public RotatingBackgrounds (Image background)
    {
        percentages = new int[] { 0 };
        minDisplayTime = new int[] { 0 };
        images = new Image[] { background };
        errorImage = images[0];
    }

    /**
     * Create a sequence of images to be rotated through from <code>backgrounds</code>.
     * 
     * Each String in backgrounds should be the path to the image, a semicolon, and the minimum
     * amount of time to display the image in seconds. Each image will be active for an equal
     * percentage of the download process, unless one hasn't been active for its minimum display
     * time when the next should be shown. In that case, it's left up until its been there for its
     * minimum display time and then the next one gets to come up.
     */
    public RotatingBackgrounds (String[] backgrounds, String errorBackground, ImageLoader loader)
    {
        percentages = new int[backgrounds.length];
        minDisplayTime = new int[backgrounds.length];
        images = new Image[backgrounds.length];
        for (int ii = 0; ii < backgrounds.length; ii++) {
            String[] pieces = backgrounds[ii].split(";");
            if (pieces.length != 2) {
                log.warning("Unable to parse background image '" + backgrounds[ii] + "'");
                makeEmpty();
                return;
            }
            images[ii] = loader.loadImage(pieces[0]);
            try {
                minDisplayTime[ii] = Integer.parseInt(pieces[1]);
            } catch (NumberFormatException e) {
                log.warning("Unable to parse background image display time '" +
                            backgrounds[ii] + "'");
                makeEmpty();
                return;
            }
            percentages[ii] = (int)((ii/(float)backgrounds.length) * 100);
        }
        if (errorBackground == null) {
            errorImage = images[0];
        } else {
            errorImage = loader.loadImage(errorBackground);
        }
    }

    /**
     * @return the image to display at the given progress or null if there aren't any.
     */
    public Image getImage (int progress)
    {
        if (images.length == 0) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (current != images.length - 1
            && (current == -1 || (progress >= percentages[current + 1] && 
                    (now - currentDisplayStart) / 1000 > minDisplayTime[current]))) {
            current++;
            currentDisplayStart = now;
        }
        return images[current];
    }

    /**
     * Returns the image to display if an error has caused getdown to fail.
     */
    public Image getErrorImage ()
    {
        return errorImage;
    }

    /**
     * @return the number of images in this RotatingBackgrounds
     */
    public int getNumImages() {
        return images.length;
    }

    protected void makeEmpty ()
    {
        percentages = new int[] {};
        minDisplayTime = new int[] {};
        images = new Image[] {};
    }

    /** Time at which the currently displayed image was first displayed in millis. */
    protected long currentDisplayStart;
    
    /** The index of the currently displayed image or -1 if we haven't displayed any. */
    protected int current = -1;
    
    protected Image[] images;
    
    /** The image to display if getdown has failed due to an error. */
    protected Image errorImage;
    
    /** Percentage at which each image should be displayed. */
    protected int[] percentages;
    
    /** Time to show each image in seconds. */
    protected int[] minDisplayTime;
}
