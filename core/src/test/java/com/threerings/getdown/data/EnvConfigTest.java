//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2018 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.io.File;

import org.junit.*;
import static org.junit.Assert.*;

public class EnvConfigTest {

    static String CWD = System.getProperty("user.dir");
    static String TESTID = "testid";
    static String TESTBASE = "https://test.com/test";

    private void debugNotes(List<EnvConfig.Note> notes) {
        for (EnvConfig.Note note : notes) {
            System.out.println(note.message);
        }
    }

    private void checkNoNotes (List<EnvConfig.Note> notes) {
        StringBuilder msg = new StringBuilder();
        for (EnvConfig.Note note : notes) {
            if (note.level != EnvConfig.Note.Level.INFO) {
                msg.append("\n").append(note.message);
            }
        }
        if (msg.length() > 0) {
            fail("Unexpected notes:" + msg.toString());
        }
    }
    private void checkDir (EnvConfig cfg) {
        assertTrue(cfg != null);
        assertEquals(new File(CWD), cfg.appDir);
    }
    private void checkNoAppId (EnvConfig cfg) {
        assertNull(cfg.appId);
    }
    private void checkAppId (EnvConfig cfg, String appId) {
        assertEquals(appId, cfg.appId);
    }
    private void checkAppBase (EnvConfig cfg, String appBase) {
        assertEquals(appBase, cfg.appBase);
    }
    private void checkNoAppBase (EnvConfig cfg) {
        assertNull(cfg.appBase);
    }
    private void checkNoAppArgs (EnvConfig cfg) {
        assertTrue(cfg.appArgs.isEmpty());
    }
    private void checkAppArgs (EnvConfig cfg, String... args) {
        assertEquals(Arrays.asList(args), cfg.appArgs);
    }

    @Test public void testArgvDir () {
        List<EnvConfig.Note> notes = new ArrayList<>();
        String[] args = { CWD };
        EnvConfig cfg = EnvConfig.create(args, notes);
        // debugNotes(notes);
        checkNoNotes(notes);
        checkDir(cfg);
        checkNoAppId(cfg);
        checkNoAppBase(cfg);
        checkNoAppArgs(cfg);
    }

    @Test public void testArgvDirId () {
        List<EnvConfig.Note> notes = new ArrayList<>();
        String[] args = { CWD, TESTID };
        EnvConfig cfg = EnvConfig.create(args, notes);
        // debugNotes(notes);
        checkNoNotes(notes);
        checkDir(cfg);
        checkAppId(cfg, TESTID);
        checkNoAppBase(cfg);
        checkNoAppArgs(cfg);
    }

    @Test public void testArgvDirArgs () {
        List<EnvConfig.Note> notes = new ArrayList<>();
        String[] args = { CWD, "", "one", "two" };
        EnvConfig cfg = EnvConfig.create(args, notes);
        // debugNotes(notes);
        checkNoNotes(notes);
        checkDir(cfg);
        checkNoAppId(cfg);
        checkNoAppBase(cfg);
        checkAppArgs(cfg, "one", "two");
    }

    @Test public void testArgvDirIdArgs () {
        List<EnvConfig.Note> notes = new ArrayList<>();
        String[] args = { CWD, TESTID, "one", "two" };
        EnvConfig cfg = EnvConfig.create(args, notes);
        // debugNotes(notes);
        checkNoNotes(notes);
        checkDir(cfg);
        checkAppId(cfg, TESTID);
        checkNoAppBase(cfg);
        checkAppArgs(cfg, "one", "two");
    }

    private EnvConfig sysPropsConfig (List<EnvConfig.Note> notes, String... keyVals) {
        for (int ii = 0; ii < keyVals.length; ii += 2) {
            System.setProperty(keyVals[ii], keyVals[ii+1]);
        }
        EnvConfig cfg = EnvConfig.create(new String[0], notes);
        for (int ii = 0; ii < keyVals.length; ii += 2) {
            System.clearProperty(keyVals[ii]);
        }
        return cfg;
    }

    @Test public void testSysPropsDir () {
        List<EnvConfig.Note> notes = new ArrayList<>();
        EnvConfig cfg = sysPropsConfig(notes, "appdir", CWD);
        // debugNotes(notes);
        checkNoNotes(notes);
        checkDir(cfg);
        checkNoAppId(cfg);
        checkNoAppBase(cfg);
        checkNoAppArgs(cfg);
    }

    @Test public void testSysPropsDirIdBase () {
        List<EnvConfig.Note> notes = new ArrayList<>();
        EnvConfig cfg = sysPropsConfig(notes, "appdir", CWD, "appid", TESTID, "appbase", TESTBASE);
        // debugNotes(notes);
        checkNoNotes(notes);
        checkDir(cfg);
        checkAppId(cfg, TESTID);
        checkAppBase(cfg, TESTBASE);
        checkNoAppArgs(cfg);
    }
}
