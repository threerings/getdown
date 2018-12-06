//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2018 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.launcher;

import java.awt.Image;
import java.util.List;

import static com.threerings.getdown.Log.log;

public final class RotatingBackgrounds
{
    public interface ImageLoader {
        /** Loads and returns the image with the supplied path. */
        public Image loadImage (String path);
    }

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
    public RotatingBackgrounds (List<String> backgrounds, String errorBackground, ImageLoader loader)
    {
        percentages = new int[backgrounds.size()];
        minDisplayTime = new int[backgrounds.size()];
        images = new Image[backgrounds.size()];
        for (int ii = 0; ii < backgrounds.size(); ii++) {
            String background = backgrounds.get(ii);
            String[] pieces = background.split(";");
            if (pieces.length != 2) {
                log.warning("Unable to parse background image '" + background + "'");
                makeEmpty();
                return;
            }
            images[ii] = loader.loadImage(pieces[0]);
            try {
                minDisplayTime[ii] = Integer.parseInt(pieces[1]);
            } catch (NumberFormatException e) {
                log.warning("Unable to parse background image display time '" + background + "'");
                makeEmpty();
                return;
            }
            percentages[ii] = (int)((ii/(float)backgrounds.size()) * 100);
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
