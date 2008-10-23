//
// $Id$
//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2006 Three Rings Design, Inc.
//
// This program is free software; you can redistribute it and/or modify it
// under the terms of the GNU General Public License as published by the Free
// Software Foundation; either version 2 of the License, or (at your option)
// any later version.
//
// This program is distributed in the hope that it will be useful, but WITHOUT
// ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
// FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
// more details.
//
// You should have received a copy of the GNU General Public License along with
// this program; if not, write to the: Free Software Foundation, Inc.,
// 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA

package com.threerings.getdown.launcher;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import java.util.MissingResourceException;
import java.util.ResourceBundle;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import com.samskivert.swing.GroupLayout;
import com.samskivert.swing.Spacer;
import com.samskivert.swing.VGroupLayout;
import com.samskivert.text.MessageUtil;

import static com.threerings.getdown.Log.log;

/**
 * Displays an interface with which the user can configure their proxy
 * settings.
 */
public class ProxyPanel extends JPanel
    implements ActionListener
{
    public ProxyPanel (Getdown getdown, ResourceBundle msgs)
    {
        _getdown = getdown;
        _msgs = msgs;

        setLayout(new VGroupLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        add(new JLabel(get("m.configure_proxy")));
        add(new Spacer(5, 5));

        JPanel row = new JPanel(new BorderLayout(5, 5));
        row.add(new JLabel(get("m.proxy_host")), BorderLayout.WEST);
        row.add(_host = new SaneTextField());
        add(row);

        row = new JPanel(new BorderLayout(5, 5));
        row.add(new JLabel(get("m.proxy_port")), BorderLayout.WEST);
        row.add(_port = new SaneTextField());
        add(row);

        add(new Spacer(5, 5));
        add(new JLabel(get("m.proxy_extra")));

        row = GroupLayout.makeButtonBox(GroupLayout.CENTER);
        JButton button;
        row.add(button = new JButton(get("m.proxy_ok")));
        button.setActionCommand("ok");
        button.addActionListener(this);
        row.add(button = new JButton(get("m.proxy_cancel")));
        button.setActionCommand("cancel");
        button.addActionListener(this);
        add(row);

        // set up any existing proxy defaults
        String host = System.getProperty("http.proxyHost");
        if (host != null) {
            _host.setText(host);
        }
        String port = System.getProperty("http.proxyPort");
        if (port != null) {
            _port.setText(port);
        }
    }

    // documentation inherited
    public void addNotify  ()
    {
        super.addNotify();
        _host.requestFocusInWindow();
    }

    // documentation inherited
    public Dimension getPreferredSize ()
    {
        // this is annoyingly hardcoded, but we can't just force the width
        // or the JLabel will claim a bogus height thinking it can lay its
        // text out all on one line which will booch the whole UI's
        // preferred size
        return new Dimension(500, 350);
    }

    // documentation inherited from interface
    public void actionPerformed (ActionEvent e)
    {
        String cmd = e.getActionCommand();
        if (cmd.equals("ok")) {
            // communicate this info back to getdown
            _getdown.configureProxy(_host.getText(), _port.getText());

        } else {
            // they canceled, we're outta here
            System.exit(0);
        }
    }

    /** Used to look up localized messages. */
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
            log.warning("Missing translation message '" + key + "'.");
            return key;
        }
    }

    protected static class SaneTextField extends JTextField
    {
        public Dimension getPreferredSize () {
            Dimension d = super.getPreferredSize();
            d.width = Math.max(d.width, 150);
            return d;
        }
    }

    protected Getdown _getdown;
    protected ResourceBundle _msgs;

    protected JTextField _host;
    protected JTextField _port;
}
