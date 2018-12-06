//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2018 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.data;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.LinkedHashSet;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@link ClassPath}.
 */
public class ClassPathTest
{
    @Before public void createJarsAndSetupClassPath () throws IOException
    {
        _firstJar = _folder.newFile("a.jar");
        _secondJar = _folder.newFile("b.jar");

        LinkedHashSet<File> classPathEntries = new LinkedHashSet<File>();
        classPathEntries.add(_firstJar);
        classPathEntries.add(_secondJar);
        _classPath = new ClassPath(classPathEntries);
    }

    @Test public void shouldCreateValidArgumentString ()
    {
        assertEquals(
            _firstJar.getAbsolutePath() + File.pathSeparator + _secondJar.getAbsolutePath(),
            _classPath.asArgumentString());
    }

    @Test public void shouldProvideJarUrls () throws MalformedURLException, URISyntaxException
    {
        URL[] actualUrls = _classPath.asUrls();
        assertEquals(_firstJar, new File(actualUrls[0].toURI()));
        assertEquals(_secondJar, new File(actualUrls[1].toURI()));
    }

    @Rule public TemporaryFolder _folder = new TemporaryFolder();

    private File _firstJar, _secondJar;
    private ClassPath _classPath;
}
