//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2018 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.launcher;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.ImageObserver;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import javax.swing.JComponent;
import javax.swing.Timer;

import com.samskivert.swing.Label;
import com.samskivert.swing.LabelStyleConstants;
import com.samskivert.swing.util.SwingUtil;
import com.samskivert.util.Throttle;
import com.threerings.getdown.data.Application.UpdateInterface;
import com.threerings.getdown.util.MessageUtil;
import com.threerings.getdown.util.Rectangle;
import static com.threerings.getdown.Log.log;

/**
 * Displays download and patching status.
 */
public final class StatusPanel extends JComponent
    implements ImageObserver
{
    public StatusPanel (ResourceBundle msgs)
    {
        _msgs = msgs;

        // Add a bit of "throbbing" to the display by updating the number of dots displayed after
        // our status. This lets users know things are still working.
        _timer = new Timer(1000,
            new ActionListener() {
                public void actionPerformed (ActionEvent event) {
                    if (_status != null && !_displayError) {
                        _statusDots = (_statusDots % 3) + 1; // 1, 2, 3, 1, 2, 3, etc.
                        updateStatusLabel();
                    }
                }
            });
    }

    public void init (UpdateInterface ifc, RotatingBackgrounds bg, Image barimg)
    {
        _ifc = ifc;
        _bg = bg;
        Image img = _bg.getImage(_progress);
        int width = img == null ? -1 : img.getWidth(this);
        int height = img == null ? -1 : img.getHeight(this);
        if (width == -1 || height == -1) {
            Rectangle bounds = ifc.progress.union(ifc.status);
            // assume the x inset defines the frame padding; add it on the left, right, and bottom
            _psize = new Dimension(bounds.x + bounds.width + bounds.x,
                                   bounds.y + bounds.height + bounds.x);
        } else {
            _psize = new Dimension(width, height);
        }
        _barimg = barimg;
        invalidate();
    }

    @Override
    public boolean imageUpdate (Image img, int infoflags, int x, int y, int width, int height)
    {
        boolean updated = false;
        if ((infoflags & WIDTH) != 0) {
            _psize.width = width;
            updated = true;
        }
        if ((infoflags & HEIGHT) != 0) {
            _psize.height = height;
            updated = true;
        }
        if (updated) {
            invalidate();
            setSize(_psize);
            getParent().setSize(_psize);
        }
        return (infoflags & ALLBITS) == 0;
    }

    /**
     * Adjusts the progress display to the specified percentage.
     */
    public void setProgress (int percent, long remaining)
    {
        boolean needsRepaint = false;

        // maybe update the progress label
        if (_progress != percent) {
            _progress = percent;
            if (!_ifc.hideProgressText) {
                String msg = MessageFormat.format(get("m.complete"), percent);
                _newplab = createLabel(msg, new Color(_ifc.progressText, true));
            }
            needsRepaint = true;
        }

        // maybe update the remaining label
        if (remaining > 1) {
            // skip this estimate if it's been less than a second since our last one came in
            if (!_rthrottle.throttleOp()) {
                _remain[_ridx++%_remain.length] = remaining;
            }

            // smooth the remaining time by taking the trailing average of the last four values
            remaining = 0;
            int values = Math.min(_ridx, _remain.length);
            for (int ii = 0; ii < values; ii++) {
                remaining += _remain[ii];
            }
            remaining /= values;

            if (!_ifc.hideProgressText) {
                // now compute our display value
                int minutes = (int)(remaining / 60), seconds = (int)(remaining % 60);
                String remstr = minutes + ":" + ((seconds < 10) ? "0" : "") + seconds;
                String msg = MessageFormat.format(get("m.remain"), remstr);
                _newrlab = createLabel(msg, new Color(_ifc.statusText, true));
            }
            needsRepaint = true;

        } else if (_rlabel != null || _newrlab != null) {
            _rthrottle = new Throttle(1, 1000);
            _ridx = 0;
            _newrlab = _rlabel = null;
            needsRepaint = true;
        }

        if (needsRepaint) {
            repaint();
        }
    }

    /**
     * Displays the specified status string.
     */
    public void setStatus (String status, boolean displayError)
    {
        _status = xlate(status);
        _displayError = displayError;
        updateStatusLabel();
    }

    /**
     * Stop the throbbing.
     */
    public void stopThrob ()
    {
        _timer.stop();
        _statusDots = 3;
        updateStatusLabel();
    }

    @Override
    public void addNotify ()
    {
        super.addNotify();
        _timer.start();
    }

    @Override
    public void removeNotify ()
    {
        _timer.stop();
        super.removeNotify();
    }

    // documentation inherited
    @Override
    public void paintComponent (Graphics g)
    {
        super.paintComponent(g);
        Graphics2D gfx = (Graphics2D)g;

        // attempt to draw a background image...
        Image img;
        if (_displayError) {
            img = _bg.getErrorImage();
        } else {
            img = _bg.getImage(_progress);
        }
        if (img != null) {
            gfx.drawImage(img, 0, 0, this);
        }

        Object oalias = SwingUtil.activateAntiAliasing(gfx);

        // if we have new labels; lay them out
        if (_newlab != null) {
            _newlab.layout(gfx);
            _label = _newlab;
            _newlab = null;
        }
        if (_newplab != null) {
            _newplab.layout(gfx);
            _plabel = _newplab;
            _newplab = null;
        }
        if (_newrlab != null) {
            _newrlab.layout(gfx);
            _rlabel = _newrlab;
            _newrlab = null;
        }

        if (_barimg != null) {
            gfx.setClip(_ifc.progress.x, _ifc.progress.y,
                        _progress * _ifc.progress.width / 100,
                        _ifc.progress.height);
            gfx.drawImage(_barimg, _ifc.progress.x, _ifc.progress.y, null);
            gfx.setClip(null);
        } else {
            gfx.setColor(new Color(_ifc.progressBar, true));
            gfx.fillRect(_ifc.progress.x, _ifc.progress.y,
                         _progress * _ifc.progress.width / 100,
                         _ifc.progress.height);
        }

        if (_plabel != null) {
            int xmarg = (_ifc.progress.width - _plabel.getSize().width)/2;
            int ymarg = (_ifc.progress.height - _plabel.getSize().height)/2;
            _plabel.render(gfx, _ifc.progress.x + xmarg, _ifc.progress.y + ymarg);
        }

        if (_label != null) {
            _label.render(gfx, _ifc.status.x, getStatusY(_label));
        }

        if (_rlabel != null) {
            // put the remaining label at the end of the status area. This could be dangerous
            // but I think the only time we would display it is with small statuses.
            int x = _ifc.status.x + _ifc.status.width - _rlabel.getSize().width;
            _rlabel.render(gfx, x, getStatusY(_rlabel));
        }

        SwingUtil.restoreAntiAliasing(gfx, oalias);
    }

    // documentation inherited
    @Override
    public Dimension getPreferredSize ()
    {
        return _psize;
    }

    /**
     * Update the status label.
     */
    private void updateStatusLabel()
    {
        StringBuilder status = new StringBuilder(_status);
        if (!_displayError) {
            for (int ii = 0; ii < _statusDots; ii++) {
                status.append(" .");
            }
        }
        _newlab = createLabel(status.toString(), new Color(_ifc.statusText, true));
        // set the width of the label to the width specified
        int width = _ifc.status.width;
        if (width == 0) {
            // unless we had trouble reading that width, in which case use the entire window
            width = getWidth();
        }
        // but the window itself might not be initialized and have a width of 0
        if (width > 0) {
            _newlab.setTargetWidth(width);
        }
        repaint();
    }

    /**
     * Get the y coordinate of a label in the status area.
     */
    private int getStatusY(Label label)
    {
        // if the status region is higher than the progress region, we
        // want to align the label with the bottom of its region
        // rather than the top
        if (_ifc.status.y > _ifc.progress.y) {
            return _ifc.status.y;
        }
        return _ifc.status.y + (_ifc.status.height - label.getSize().height);
    }

    /**
     * Create a label, taking care of adding the shadow if needed.
     */
    private Label createLabel(String text, Color color)
    {
        Label label = new Label(text, color, FONT);
        if (_ifc.textShadow != 0) {
            label.setAlternateColor(new Color(_ifc.textShadow, true));
            label.setStyle(LabelStyleConstants.SHADOW);
        }
        return label;
    }

    /** Used by {@link #setStatus}. */
    private String xlate(String compoundKey)
    {
        // to be more efficient about creating unnecessary objects, we
        // do some checking before splitting
        int tidx = compoundKey.indexOf('|');
        if (tidx == -1) {
            return get(compoundKey);

        } else {
            String key = compoundKey.substring(0, tidx);
            String argstr = compoundKey.substring(tidx+1);
            String[] args = argstr.split("\\|");
            // unescape and translate the arguments
            for (int i = 0; i < args.length; i++) {
                // if the argument is tainted, do no further translation
                // (it might contain |s or other fun stuff)
                if (MessageUtil.isTainted(args[i])) {
                    args[i] = MessageUtil.unescape(MessageUtil.untaint(args[i]));
                } else {
                    args[i] = xlate(MessageUtil.unescape(args[i]));
                }
            }
            return get(key, args);
        }
    }

    /** Used by {@link #setStatus}. */
    private String get(String key, String[] args)
    {
        String msg = get(key);
        if (msg != null) return MessageFormat.format(MessageUtil.escape(msg), (Object[])args);
        return key + Arrays.asList(args);
    }

    /** Used by {@link #setStatus}, and {@link #setProgress}. */
    private String get(String key)
    {
        // if we have no _msgs that means we're probably recovering from a
        // failure to load the translation messages in the first place, so
        // just give them their key back because it's probably an english
        // string; whee!
        if (_msgs == null) {
            return key;
        }

        // if this string is tainted, we don't translate it, instead we
        // simply remove the taint character and return it to the caller
        if (MessageUtil.isTainted(key)) {
            return MessageUtil.untaint(key);
        }
        try {
            return _msgs.getString(key);
        } catch (MissingResourceException mre) {
            log.warning("Missing translation message '" + key + "'.");
            return key;
        }
    }

    private Image _barimg;
    private RotatingBackgrounds _bg;
    private Dimension _psize;

    private final ResourceBundle _msgs;

    private int _progress = -1;
    private String _status;
    private int _statusDots = 1;
    private boolean _displayError;
    private Label _label, _newlab;
    private Label _plabel, _newplab;
    private Label _rlabel, _newrlab;

    private UpdateInterface _ifc;
    private final Timer _timer;

    private final long[] _remain = new long[4];
    private int _ridx;
    private Throttle _rthrottle = new Throttle(1, 1000L);

    static final Font FONT = new Font("SansSerif", Font.BOLD, 12);
}
