//
// $Id: StatusPanel.java,v 1.3 2004/07/19 12:39:08 mdb Exp $

package com.threerings.getdown.launcher;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import javax.swing.JComponent;

import java.text.MessageFormat;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import com.samskivert.swing.Label;
import com.samskivert.swing.util.SwingUtil;
import com.samskivert.text.MessageUtil;
import com.samskivert.util.StringUtil;

import com.threerings.getdown.Log;

/**
 * Displays download and patching status.
 */
public class StatusPanel extends JComponent
{
    public StatusPanel (ResourceBundle msgs, Rectangle bounds,
                        BufferedImage bgimg, Rectangle ppos, Rectangle spos)
    {
        _msgs = msgs;
        _bgimg = bgimg;
        _psize = new Dimension(bounds.width, bounds.height);
        _ppos = ppos;
        _spos = spos;
    }

    /**
     * Adjusts the progress display to the specified percentage.
     */
    public void setProgress (int percent, long remaining)
    {
        _progress = percent;
        String msg = (remaining > 1) ? "m.complete_remain" : "m.complete";
        msg = get(msg);
        String label = MessageFormat.format(msg, new Object[] {
            new Integer(percent), new Long(remaining) });
        _newplab = new Label(label);
        repaint();
    }

    /**
     * Displays the specified status string.
     */
    public void setStatus (String status)
    {
        status = xlate(status);
        _newlab = new Label(status, Color.white, null);
        _newlab.setTargetWidth(_spos.width);
        repaint();
    }

    // documentation inherited
    public void paintComponent (Graphics g)
    {
        super.paintComponent(g);
        Graphics2D gfx = (Graphics2D)g;

        if (_bgimg != null) {
            gfx.drawImage(_bgimg, 0, 0, null);
        } else {
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

        Composite ocomp = gfx.getComposite();
        gfx.setComposite(PROGRESS_ALPHA);
        gfx.setColor(Color.black);
        gfx.fillRect(_ppos.x, _ppos.y, _progress * _ppos.width / 100,
                     _ppos.height);
        gfx.setComposite(ocomp);

        gfx.setColor(Color.white);
        if (_plabel != null) {
            int xmarg = (_ppos.width - _plabel.getSize().width)/2;
            int ymarg = (_ppos.height - _plabel.getSize().height)/2;
            _plabel.render(gfx, _ppos.x + xmarg, _ppos.y + ymarg);
        }
        gfx.draw(_ppos);

        if (_label != null) {
            _label.render(gfx, _spos.x, _spos.y);
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
                if (args[i].startsWith(MessageUtil.TAINT_CHAR)) {
                    args[i] = MessageUtil.unescape(args[i].substring(1));
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
        // if this string is tainted, we don't translate it, instead we
        // simply remove the taint character and return it to the caller
        if (key.startsWith(MessageUtil.TAINT_CHAR)) {
            return key.substring(1);
        }
        try {
            return _msgs.getString(key);
        } catch (MissingResourceException mre) {
            Log.warning("Missing translation message '" + key + "'.");
            return key;
        }
    }

    protected BufferedImage _bgimg;
    protected Dimension _psize;
    protected Rectangle _ppos, _spos;

    protected ResourceBundle _msgs;

    protected int _progress = 0;
    protected Label _label, _newlab;
    protected Label _plabel, _newplab;

    /** The alpha level at which to paint the progress bar. */
    protected static final Composite PROGRESS_ALPHA =
        AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f);
}
