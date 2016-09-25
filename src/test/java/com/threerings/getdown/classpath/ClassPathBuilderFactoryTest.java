package com.threerings.getdown.classpath;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.threerings.getdown.data.Application;

/**
 * Checks wether the correct {@link ClassPathBuilder class path builders} are created.
 */
@RunWith(MockitoJUnitRunner.class)
public class ClassPathBuilderFactoryTest
{
    @Test
    public void shouldCreateDefaultClassPathBuilder ()
    {
        when(_application.isUseCodeCache()).thenReturn(Boolean.FALSE);

        assertTrue(ClassPathBuilderFactory.create(_application) instanceof DefaultClassPathBuilder);
    }

    @Test
    public void shouldCreateCacheClassPathBuilder ()
    {
        when(_application.isUseCodeCache()).thenReturn(Boolean.TRUE);

        assertTrue(ClassPathBuilderFactory.create(_application) instanceof CacheBasedClassPathBuilder);
    }

    @Mock
    private Application _application;
}
