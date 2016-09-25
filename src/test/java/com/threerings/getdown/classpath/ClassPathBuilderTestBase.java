package com.threerings.getdown.classpath;

import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;

import com.threerings.getdown.data.Application;
import com.threerings.getdown.data.Resource;

/**
 * Base class for {@link ClassPathBuilder} tests.
 */
public abstract class ClassPathBuilderTestBase {
    @Before
    public void setupFilesAndResources () throws IOException
    {
        _firstJarFile = _appdir.newFile("a.jar");
        _secondJarFile = _appdir.newFile("b.jar");

        when(_firstJar.getFinalTarget()).thenReturn(_firstJarFile);
        when(_secondJar.getFinalTarget()).thenReturn(_secondJarFile);
        when(_application.getActiveCodeResources()).thenReturn(Arrays.asList(_firstJar, _secondJar));
        when(_application.getAppdir()).thenReturn(_appdir.getRoot());
    }

    @Mock
    protected Application _application;

    @Mock
    protected Resource _firstJar;

    protected File _firstJarFile;

    @Mock
    protected Resource _secondJar;

    protected File _secondJarFile;

    @Rule
    public TemporaryFolder _appdir = new TemporaryFolder();
}
