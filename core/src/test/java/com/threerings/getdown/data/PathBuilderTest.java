//
// Getdown - application installer, patcher and launcher
// Copyright (C) 2004-2018 Getdown authors
// https://github.com/threerings/getdown/blob/master/LICENSE

package com.threerings.getdown.data;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import static org.junit.Assert.assertEquals;

import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class PathBuilderTest
{
    @Before public void setupFilesAndResources () throws IOException
    {
        _firstJarFile = _appdir.newFile("a.jar");
        _secondJarFile = _appdir.newFile("b.jar");

        when(_firstJar.getFinalTarget()).thenReturn(_firstJarFile);
        when(_secondJar.getFinalTarget()).thenReturn(_secondJarFile);
        when(_application.getActiveCodeResources()).thenReturn(Arrays.asList(_firstJar, _secondJar));
        when(_application.getAppDir()).thenReturn(_appdir.getRoot());
    }

    @Test public void shouldBuildDefaultClassPath () throws IOException
    {
        ClassPath classPath = PathBuilder.buildDefaultClassPath(_application);
        String expectedClassPath = _firstJarFile.getAbsolutePath() + File.pathSeparator +
            _secondJarFile.getAbsolutePath();
        assertEquals(expectedClassPath, classPath.asArgumentString());
    }

    @Test public void shouldBuildCachedClassPath () throws IOException
    {
        when(_application.getDigest(_firstJar)).thenReturn("first");
        when(_application.getDigest(_secondJar)).thenReturn("second");
        when(_application.getCodeCacheRetentionDays()).thenReturn(1);

        Path firstCachedJarFile = _appdir.getRoot().toPath().
            resolve(PathBuilder.CODE_CACHE_DIR).resolve("fi").resolve("first.jar");

        Path secondCachedJarFile = _appdir.getRoot().toPath().
            resolve(PathBuilder.CODE_CACHE_DIR).resolve("se").resolve("second.jar");

        String expectedClassPath = firstCachedJarFile.toAbsolutePath() + File.pathSeparator +
            secondCachedJarFile.toAbsolutePath();

        ClassPath classPath = PathBuilder.buildCachedClassPath(_application);
        assertEquals(expectedClassPath, classPath.asArgumentString());
    }

    @Mock protected Application _application;
    @Mock protected Resource _firstJar;
    @Mock protected Resource _secondJar;

    protected File _firstJarFile, _secondJarFile;

    @Rule public TemporaryFolder _appdir = new TemporaryFolder();
}
