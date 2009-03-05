//
// $Id$
//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2008 Three Rings Design, Inc.
//
// Redistribution and use in source and binary forms, with or without modification, are permitted
// provided that the following conditions are met:
//
// 1. Redistributions of source code must retain the above copyright notice, this list of
//    conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright notice, this list of
//    conditions and the following disclaimer in the documentation and/or other materials provided
//    with the distribution.
//
// THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES,
// INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
// PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT,
// INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
// TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
// LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
// SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
package com.threerings.getdown.launcher;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.text.MessageFormat;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import javax.swing.JComponent;

import com.samskivert.swing.Label;
import com.samskivert.swing.util.SwingUtil;
import com.samskivert.text.MessageUtil;
import com.samskivert.util.StringUtil;
import com.samskivert.util.Throttle;

import com.threerings.getdown.data.Application.UpdateInterface;

import static com.threerings.getdown.Log.log;

/**
 * Displays download and patching status.
 */
public class StatusPanel extends JComponent
{
    public StatusPanel (ResourceBundle msgs)
    {
        _msgs = msgs;
    }

    public void init (UpdateInterface ifc, RotatingBackgrounds bg, Image barimg)
    {
        _ifc = ifc;
        _bg = bg;
        Image img = _bg.getImage(_progress);
        if (img == null) {
            Rectangle bounds = ifc.progress.union(ifc.status);
            bounds.grow(5, 5);
            _psize = bounds.getSize();
        } else {
            _psize = new Dimension(img.getWidth(null), img.getHeight(null));
        }
        _barimg = barimg;
    }

    /**
     * Adjusts the progress display to the specified percentage.
     */
    public void setProgress (int percent, long remaining)
    {
        _progress = percent;
        String msg = "m.complete";
        String remstr = "";
        if (remaining > 1) {
            // skip this estimate if it's been less than a second since
            // our last one came in
            if (!_rthrottle.throttleOp()) {
                _remain[_ridx++%_remain.length] = remaining;
            }

            // smooth the remaining time by taking the trailing average of
            // the last four values
            remaining = 0;
            int values = Math.min(_ridx, _remain.length);
            for (int ii = 0; ii < values; ii++) {
                remaining += _remain[ii];
            }
            remaining /= values;

            // now compute our display value
            msg = "m.complete_remain";
            int minutes = (int)(remaining / 60);
            int seconds = (int)(remaining % 60);
            remstr = minutes + ":";
            if (seconds < 10) {
                remstr += "0";
            }
            remstr += seconds;
        }
        msg = get(msg);
        String label = MessageFormat.format(msg, new Object[] {
            new Integer(percent), remstr });
        _newplab = new Label(label, _ifc.progressText, _font);
        if (_ifc.textShadow != null) {
            _newplab.setAlternateColor(_ifc.textShadow);
            _newplab.setStyle(Label.SHADOW);
        }
        repaint();
    }

    /**
     * Displays the specified status string.
     */
    public void setStatus (String status, boolean displayError)
    {
        _displayError = displayError;
        status = xlate(status);
        _newlab = new Label(status, _ifc.statusText, _font);
        int width = getWidth() - _ifc.status.x*2;
        width = width > 0 ? Math.min(_ifc.status.width, width) : _ifc.status.width;
        _newlab.setTargetWidth(width);
        if (_ifc.textShadow != null) {
            _newlab.setAlternateColor(_ifc.textShadow);
            _newlab.setStyle(Label.SHADOW);
        }
        repaint();
    }

    // documentation inherited
    public void paintComponent (Graphics g)
    {
        super.paintComponent(g);
        Graphics2D gfx = (Graphics2D)g;

        Image img;
        if (_displayError) {
            img = _bg.getErrorImage();
        } else {
            img = _bg.getImage(_progress);
        }
        if (img != null) {
            gfx.drawImage(img, 0, 0, this);
        } else {
            gfx.setColor(getBackground());
            gfx.fillRect(0, 0, getWidth(), getHeight());
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

        if (_barimg != null) {
            gfx.setClip(_ifc.progress.x, _ifc.progress.y,
                        _progress * _ifc.progress.width / 100,
                        _ifc.progress.height);
            gfx.drawImage(_barimg, _ifc.progress.x, _ifc.progress.y, null);
            gfx.setClip(null);
        } else {
            gfx.setColor(_ifc.progressBar);
            gfx.fillRect(_ifc.progress.x, _ifc.progress.y,
                         _progress * _ifc.progress.width / 100,
                         _ifc.progress.height);
        }

        if (_plabel != null) {
            int xmarg = (_ifc.progress.width - _plabel.getSize().width)/2;
            int ymarg = (_ifc.progress.height - _plabel.getSize().height)/2;
            _plabel.render(gfx, _ifc.progress.x + xmarg,
                           _ifc.progress.y + ymarg);
        }

        if (_label != null) {
            // if the status region is higher than the progress region, we
            // want to align the label with the bottom of its region
            // rather than the top
            int ly;
            if (_ifc.status.y > _ifc.progress.y) {
                ly = _ifc.status.y;
            } else {
                ly = _ifc.status.y + (_ifc.status.height -
                                      _label.getSize().height);
            }
            _label.render(gfx, _ifc.status.x, ly);
        }

        SwingUtil.restoreAntiAliasing(gfx, oalias);
    }

    // documentation inherited
    public Dimension getPreferredSize ()
    {
        return _psize;
    }

    /** Used by {@link #setStatus}. */
    protected String xlate (String compoundKey)
    {
        // to be more efficient about creating unnecessary objects, we
        // do some checking before splitting
        int tidx = compoundKey.indexOf('|');
        if (tidx == -1) {
            return get(compoundKey);

        } else {
            String key = compoundKey.substring(0, tidx);
            String argstr = compoundKey.substring(tidx+1);
            String[] args = StringUtil.split(argstr, "|");
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
    protected String get (String key, Object[] args)
    {
        String msg = get(key);
        return (msg != null) ?
            MessageFormat.format(MessageUtil.escape(msg), args)
            : (key + StringUtil.toString(args));
    }

    /** Used by {@link #setStatus}, and {@link #setProgress}. */
    protected String get (String key)
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

    protected Image _barimg;
    protected RotatingBackgrounds _bg;
    protected Dimension _psize;

    protected ResourceBundle _msgs;

    protected int _progress = 0;
    protected boolean _displayError;
    protected Label _label, _newlab;
    protected Label _plabel, _newplab;

    protected UpdateInterface _ifc;

    protected long[] _remain = new long[4];
    protected int _ridx;
    protected Throttle _rthrottle = new Throttle(1, 1000L);

    protected static final Font _font = new Font("SansSerif", Font.BOLD, 12);
}
