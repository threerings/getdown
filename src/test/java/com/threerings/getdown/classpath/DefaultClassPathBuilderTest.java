package com.threerings.getdown.classpath;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

/**
 * Tests the {@link DefaultClassPathBuilder}.
 */
@RunWith(MockitoJUnitRunner.class)
public class DefaultClassPathBuilderTest extends ClassPathBuilderTestBase
{
    @Test
    public void shouldBuildClassPath () throws IOException
    {
        ClassPath classPath = new DefaultClassPathBuilder(_application).buildClassPath();

        String expectedClassPath = _firstJarFile.getAbsolutePath()
                + File.pathSeparator
                + _secondJarFile.getAbsolutePath();

        assertEquals(expectedClassPath, classPath.asArgumentString());
    }


}
