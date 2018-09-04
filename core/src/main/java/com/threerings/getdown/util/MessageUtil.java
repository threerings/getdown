//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2016 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.util;

public class MessageUtil {

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
     * Call this to "taint" any string that has been entered by an entity outside the application
     * so that the translation code knows not to attempt to translate this string when doing
     * recursive translations.
     */
    public static String taint (Object text)
    {
        return TAINT_CHAR + text;
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

    /** Text prefixed by this character will be considered tainted when doing recursive
     * translations and won't be translated. */
    protected static final String TAINT_CHAR = "~";
}
