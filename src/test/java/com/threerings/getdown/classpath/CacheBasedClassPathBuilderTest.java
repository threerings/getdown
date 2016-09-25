package com.threerings.getdown.classpath;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import com.threerings.getdown.util.FileUtil;

/**
 * Tests {@link CacheBasedClassPathBuilderTest}.
 */
@RunWith(MockitoJUnitRunner.class)
public class CacheBasedClassPathBuilderTest extends ClassPathBuilderTestBase
{
    @Test
    public void shouldBuildCacheBasedClassPath () throws IOException
    {
        when(_application.getDigest(_firstJar)).thenReturn("first");
        when(_application.getDigest(_secondJar)).thenReturn("second");
        when(_application.getCodeCacheRetentionDays()).thenReturn(1);

        File firstCachedJarFile = FileUtil.newFile(
                _appdir.getRoot(),
                CacheBasedClassPathBuilder.CACHE_DIR, "fi", "first.jar");

        File secondCachedJarFile = FileUtil.newFile(
                _appdir.getRoot(),
                CacheBasedClassPathBuilder.CACHE_DIR, "se", "second.jar");

        String expectedClassPath = firstCachedJarFile.getAbsolutePath()
                + File.pathSeparator
                + secondCachedJarFile.getAbsolutePath();

        ClassPath classPath = new CacheBasedClassPathBuilder(_application).buildClassPath();

        assertEquals(expectedClassPath, classPath.asArgumentString());
    }
}
