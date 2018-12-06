//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2018 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.util;

public class MessageUtil {

    /**
     * Returns whether or not the provided string is tainted. See {@link #taint}. Null strings
     * are considered untainted.
     */
    public static boolean isTainted (String text)
    {
        return text != null && text.startsWith(TAINT_CHAR);
    }

    /**
     * Call this to "taint" any string that has been entered by an entity outside the application
     * so that the translation code knows not to attempt to translate this string when doing
     * recursive translations.
     */
    public static String taint (Object text)
    {
        return TAINT_CHAR + text;
    }

    /**
     * Removes the tainting character added to a string by {@link #taint}. If the provided string
     * is not tainted, this silently returns the originally provided string.
     */
    public static String untaint (String text)
    {
        return isTainted(text) ? text.substring(TAINT_CHAR.length()) : text;
    }

    /**
     * Composes a message key with an array of arguments. The message can subsequently be
     * decomposed and translated without prior knowledge of how many arguments were provided.
     */
    public static String compose (String key, Object... args)
    {
        StringBuilder buf = new StringBuilder();
        buf.append(key);
        buf.append('|');
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                buf.append('|');
            }
            // escape the string while adding to the buffer
            String arg = (args[i] == null) ? "" : String.valueOf(args[i]);
            int alength = arg.length();
            for (int p = 0; p < alength; p++) {
                char ch = arg.charAt(p);
                if (ch == '|') {
                    buf.append("\\!");
                } else if (ch == '\\') {
                    buf.append("\\\\");
                } else {
                    buf.append(ch);
                }
            }
        }
        return buf.toString();
    }

    /**
     * Compose a message with String args. This is just a convenience so callers do not have to
     * cast their String[] to an Object[].
     */
    public static String compose (String key, String... args)
    {
        return compose(key, (Object[]) args);
    }

    /**
     * A convenience method for calling {@link #compose(String,Object[])} with an array of
     * arguments that will be automatically tainted (see {@link #taint}).
     */
    public static String tcompose (String key, Object... args)
    {
        int acount = args.length;
        String[] targs = new String[acount];
        for (int ii = 0; ii < acount; ii++) {
            targs[ii] = taint(args[ii]);
        }
        return compose(key, (Object[]) targs);
    }

    /**
     * A convenience method for calling {@link #compose(String,String[])} with an array of argument
     * that will be automatically tainted.
     */
    public static String tcompose (String key, String... args)
    {
        for (int ii = 0, nn = args.length; ii < nn; ii++) {
            args[ii] = taint(args[ii]);
        }
        return compose(key, args);
    }

    /**
     * Used to escape single quotes so that they are not interpreted by <code>MessageFormat</code>.
     * As we assume all single quotes are to be escaped, we cannot use the characters
     * <code>{</code> and <code>}</code> in our translation strings, but this is a small price to
     * pay to have to differentiate between messages that will and won't eventually be parsed by a
     * <code>MessageFormat</code> instance.
     */
    public static String escape (String message)
    {
        return message.replace("'", "''");
    }

    /**
     * Unescapes characters that are escaped in a call to compose.
     */
    public static String unescape (String value)
    {
        int bsidx = value.indexOf('\\');
        if (bsidx == -1) {
            return value;
        }

        StringBuilder buf = new StringBuilder();
        int vlength = value.length();
        for (int ii = 0; ii < vlength; ii++) {
            char ch = value.charAt(ii);
            if (ch != '\\' || ii == vlength-1) {
                buf.append(ch);
            } else {
                // look at the next character
                ch = value.charAt(++ii);
                buf.append((ch == '!') ? '|' : ch);
            }
        }

        return buf.toString();
    }

    /** Text prefixed by this character will be considered tainted when doing recursive
     * translations and won't be translated. */
    protected static final String TAINT_CHAR = "~";
}
