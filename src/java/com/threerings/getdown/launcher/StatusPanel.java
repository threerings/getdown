//
// $Id: StatusPanel.java,v 1.1 2004/07/07 08:42:40 mdb Exp $

package com.threerings.getdown.launcher;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import javax.swing.JComponent;

import com.samskivert.swing.Label;

/**
 * Displays download and patching status.
 */
public class StatusPanel extends JComponent
{
    public StatusPanel (Rectangle bounds, BufferedImage bgimg,
                        Rectangle ppos, Rectangle spos)
    {
        _bgimg = bgimg;
        _psize = new Dimension(bounds.width, bounds.height);
        _ppos = ppos;
        _spos = spos;
    }

    /**
     * Adjusts the progress display to the specified percentage.
     */
    public void setProgress (int percent)
    {
        _progress = percent;
        repaint();
    }

    /**
     * Displays the specified status string.
     */
    public void setStatus (String status)
    {
        _newlab = new Label(status, Color.white, null);
        _newlab.setTargetWidth(_spos.width);
        repaint();
    }

    // documentation inherited
    public void paintComponent (Graphics g)
    {
        super.paintComponent(g);
        Graphics2D gfx = (Graphics2D)g;

        // if we have a new label; lay it out
        if (_newlab != null) {
            _newlab.layout(gfx);
            _label = _newlab;
            _newlab = null;
        }

        if (_bgimg != null) {
            gfx.drawImage(_bgimg, 0, 0, null);
        } else {
            gfx.fillRect(0, 0, getWidth(), getHeight());
        }

        gfx.setColor(Color.blue);
        gfx.fillRect(_ppos.x, _ppos.y, _progress * _ppos.width / 100,
                     _ppos.height);

        gfx.setColor(Color.white);
        gfx.draw(_ppos);

        if (_label != null) {
            _label.render(gfx, _spos.x, _spos.y);
        }
    }

    // documentation inherited
    public Dimension getPreferredSize ()
    {
        return _psize;
    }

    protected BufferedImage _bgimg;
    protected Dimension _psize;
    protected Rectangle _ppos, _spos;

    protected int _progress = 0;
    protected Label _label, _newlab;
}
