//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2014 Three Rings Design, Inc.
// https://raw.github.com/threerings/getdown/master/LICENSE

package com.threerings.getdown.launcher;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import java.util.MissingResourceException;
import java.util.ResourceBundle;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.samskivert.swing.GroupLayout;
import com.samskivert.swing.Spacer;
import com.samskivert.swing.VGroupLayout;
import com.samskivert.text.MessageUtil;

import static com.threerings.getdown.Log.log;

/**
 * Displays a confirmation that the user wants to abort installation.
 */
public class AbortPanel extends JFrame
    implements ActionListener
{
    public AbortPanel (Getdown getdown, ResourceBundle msgs)
    {
        _getdown = getdown;
        _msgs = msgs;
 
        setLayout(new VGroupLayout());
        setResizable(false);
        setTitle(get("m.abort_title"));

        JLabel message = new JLabel(get("m.abort_confirm"));
        message.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        add(message);
        add(new Spacer(5, 5));

        JPanel row = GroupLayout.makeButtonBox(GroupLayout.CENTER);
        JButton button;
        row.add(button = new JButton(get("m.abort_ok")));
        button.setActionCommand("ok");
        button.addActionListener(this);
        row.add(button = new JButton(get("m.abort_cancel")));
        button.setActionCommand("cancel");
        button.addActionListener(this);
        getRootPane().setDefaultButton(button);
        add(row);
    }

    // documentation inherited
    @Override
    public Dimension getPreferredSize ()
    {
        // this is annoyingly hardcoded, but we can't just force the width
        // or the JLabel will claim a bogus height thinking it can lay its
        // text out all on one line which will booch the whole UI's
        // preferred size
        return new Dimension(300, 200);
    }

    // documentation inherited from interface
    public void actionPerformed (ActionEvent e)
    {
        String cmd = e.getActionCommand();
        if (cmd.equals("ok")) {
            System.exit(0);
        } else {
            setVisible(false);
        }
    }

    /** Used to look up localized messages. */
    protected String get (String key)
    {
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

    protected Getdown _getdown;
    protected ResourceBundle _msgs;
}
