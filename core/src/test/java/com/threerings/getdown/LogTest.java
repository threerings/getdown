package com.threerings.getdown;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class LogTest {
    @Test
    public void testLog() {
        // given
        final List<LogRecord> logRecords = new ArrayList<>();
        final FileNotFoundException ex = new FileNotFoundException("patch.dat");

        class TestHandler extends Handler {
            @Override
            public void publish (LogRecord logRecord) {
                logRecords.add(logRecord);
            }

            @Override
            public void flush () {
            }

            @Override
            public void close () throws SecurityException {
            }
        }

        Log.log._impl.addHandler(new TestHandler());

        // when
        Log.log.warning("Download failed", "rsrc", "Resource", ex);

        // then
        assertEquals("Download failed [rsrc=Resource]", logRecords.get(0).getMessage());
        assertSame(ex, logRecords.get(0).getThrown());
    }
}
