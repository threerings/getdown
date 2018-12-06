//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2018 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.FieldPosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.*;

/**
 * A placeholder class that contains a reference to the log object used by the Getdown code.
 */
public class Log
{
    public static class Shim {
        /**
         * Logs a debug message.
         *
         * @param message the message to be logged.
         * @param args a list of key/value pairs and an optional final Throwable.
         */
        public void debug (Object message, Object... args) { doLog(0, message, args); }

        /**
         * Logs an info message.
         *
         * @param message the message to be logged.
         * @param args a list of key/value pairs and an optional final Throwable.
         */
        public void info (Object message, Object... args) { doLog(1, message, args); }

        /**
         * Logs a warning message.
         *
         * @param message the message to be logged.
         * @param args a list of key/value pairs and an optional final Throwable.
         */
        public void warning (Object message, Object... args) { doLog(2, message, args); }

        /**
         * Logs an error message.
         *
         * @param message the message to be logged.
         * @param args a list of key/value pairs and an optional final Throwable.
         */
        public void error (Object message, Object... args) { doLog(3, message, args); }

        protected void doLog (int levIdx, Object message, Object[] args) {
            if (_impl.isLoggable(LEVELS[levIdx])) {
                Throwable err = null;
                int nn = args.length;
                if (message instanceof Throwable) {
                    err = (Throwable)message;
                } else if (nn % 2 == 1 && (args[nn - 1] instanceof Throwable)) {
                    err = (Throwable)args[--nn];
                }
                _impl.log(LEVELS[levIdx], format(message, args), err);
            }
        }

        protected final Logger _impl = Logger.getLogger("com.threerings.getdown");
    }

    /** We dispatch our log messages through this logging shim. */
    public static final Shim log = new Shim();

    public static String format (Object message, Object... args) {
        if (args.length < 2) return String.valueOf(message);
        StringBuilder buf = new StringBuilder(String.valueOf(message));
        if (buf.length() > 0) {
            buf.append(' ');
        }
        buf.append('[');
        for (int ii = 0; ii < args.length; ii += 2) {
            if (ii > 0) {
                buf.append(',').append(' ');
            }
            buf.append(args[ii]).append('=');
            try {
                buf.append(args[ii+1]);
            } catch (Throwable t) {
                buf.append("<toString() failure: ").append(t).append(">");
            }
        }
        return buf.append(']').toString();
    }

    static {
        Formatter formatter = new OneLineFormatter();
        Logger logger = LogManager.getLogManager().getLogger("");
        for (Handler handler : logger.getHandlers()) {
            handler.setFormatter(formatter);
        }
    }

    protected static class OneLineFormatter extends Formatter {
        @Override public String format (LogRecord record) {
            StringBuffer buf = new StringBuffer();

            // append the timestamp
            _date.setTime(record.getMillis());
            _format.format(_date, buf, _fpos);

            // append the log level
            buf.append(" ");
            buf.append(record.getLevel().getLocalizedName());
            buf.append(" ");

            // append the message itself
            buf.append(formatMessage(record));
            buf.append(System.lineSeparator());

            // if an exception was also provided, append that
            if (record.getThrown() != null) {
                try {
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    record.getThrown().printStackTrace(pw);
                    pw.close();
                    buf.append(sw.toString());
                } catch (Exception ex) {
                    buf.append("Format failure:").append(ex);
                }
            }

            return buf.toString();
        }

        protected Date _date = new Date();
        protected SimpleDateFormat _format = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss:SSS");
        protected FieldPosition _fpos = new FieldPosition(SimpleDateFormat.DATE_FIELD);
    }

    protected static final String DATE_FORMAT = "{0,date} {0,time}";
    protected static final Level[] LEVELS = {Level.FINE, Level.INFO, Level.WARNING, Level.SEVERE};
}
