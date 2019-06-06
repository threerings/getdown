//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2018 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.data;

import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.junit.*;
import static org.junit.Assert.*;

import com.threerings.getdown.util.Config;

public class ApplicationTest {

    Application createApp () {
        List<EnvConfig.Note> notes = new ArrayList<>();
        EnvConfig env = EnvConfig.create(new String[0], notes);
        EnvConfigTest.checkNoNotes(notes);
        return new Application(env);
    }

    @Test public void testBaseConfig () throws Exception {
        Application app = createApp();
        URL appbase = new URL("https://test.com/foo/bar/");
        Config config = new Config(Config.parseData(toReader(
            "appbase", appbase.toString()
        ), Config.createOpts(true)));
        app.initBase(config);

        assertEquals(appbase, app.getRemoteURL(""));
    }

    @Test public void testVersionedBase () throws Exception {
        Application app = createApp();
        String rootAppbase = "https://test.com/foo/bar/";
        Config config = new Config(Config.parseData(toReader(
            "appbase", rootAppbase + "%VERSION%",
            "version", "42"
        ), Config.createOpts(true)));
        app.initBase(config);

        assertEquals(new URL(rootAppbase + "42/"), app.getRemoteURL(""));
    }

    @Test public void testEnvVarBase () throws Exception {
        // fiddling to make test work on Windows or Unix
        String evar = System.getenv("USER") == null ? "USERNAME" : "USER";
        Application app = createApp();
        String rootAppbase = "https://test.com/foo/%ENV." + evar + "%/";
        Config config = new Config(Config.parseData(toReader(
            "appbase", rootAppbase + "%VERSION%",
            "version", "42"
        ), Config.createOpts(true)));
        app.initBase(config);

        String expectAppbase = "https://test.com/foo/" + System.getenv(evar) + "/42/";
        assertEquals(new URL(expectAppbase), app.getRemoteURL(""));
    }

    protected static StringReader toReader (String... pairs)
    {
        StringBuilder builder = new StringBuilder();
        for (int ii = 0; ii < pairs.length; ii += 2) {
            builder.append(pairs[ii]).append("=").append(pairs[ii+1]).append("\n");
        }
        return new StringReader(builder.toString());
    }

}
