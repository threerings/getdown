package com.threerings.getdown.classpath;

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

import com.threerings.getdown.data.Application;
import com.threerings.getdown.data.Resource;

@RunWith(MockitoJUnitRunner.class)
public class ClassPathsTest
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
        ClassPath classPath = ClassPaths.buildDefaultClassPath(_application);
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
            resolve(ClassPaths.CACHE_DIR).resolve("fi").resolve("first.jar");

        Path secondCachedJarFile = _appdir.getRoot().toPath().
            resolve(ClassPaths.CACHE_DIR).resolve("se").resolve("second.jar");

        String expectedClassPath = firstCachedJarFile.toAbsolutePath() + File.pathSeparator +
            secondCachedJarFile.toAbsolutePath();

        ClassPath classPath = ClassPaths.buildCachedClassPath(_application);
        assertEquals(expectedClassPath, classPath.asArgumentString());
    }

    @Mock protected Application _application;
    @Mock protected Resource _firstJar;
    @Mock protected Resource _secondJar;

    protected File _firstJarFile, _secondJarFile;

    @Rule public TemporaryFolder _appdir = new TemporaryFolder();
}
