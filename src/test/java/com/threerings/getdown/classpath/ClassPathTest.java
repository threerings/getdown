package com.threerings.getdown.classpath;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.LinkedHashSet;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Tests for {@link ClassPath}.
 */
public class ClassPathTest
{
    @Before
    public void createJarsAndSetupClassPath () throws IOException
    {
        _firstJar = _folder.newFile("a.jar");
        _secondJar = _folder.newFile("b.jar");

        LinkedHashSet<ClassPathElement> classPathEntries = new LinkedHashSet<ClassPathElement>();

        classPathEntries.add(new ClassPathElement(_firstJar));
        classPathEntries.add(new ClassPathElement(_secondJar));

        _classPath = new ClassPath(classPathEntries);
    }

    @Test
    public void shouldCreateValidArgumentString ()
    {
        assertEquals(
                _firstJar.getAbsolutePath() + File.pathSeparator + _secondJar.getAbsolutePath(),
                _classPath.asArgumentString());
    }

    @Test
    public void shouldProvideJarUrls () throws MalformedURLException, URISyntaxException
    {
        URL[] actualUrls = _classPath.asUrls();

        assertEquals(_firstJar, new File(actualUrls[0].toURI()));
        assertEquals(_secondJar, new File(actualUrls[1].toURI()));
    }

    @Rule
    public TemporaryFolder _folder = new TemporaryFolder();

    private File _firstJar;
    private File _secondJar;

    private ClassPath _classPath;
}
