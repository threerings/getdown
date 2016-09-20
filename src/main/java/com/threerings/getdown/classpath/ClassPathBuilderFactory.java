package com.threerings.getdown.classpath;

import com.threerings.getdown.data.Application;

/**
 * A factory for {@link ClassPathBuilder class path builders}.
 */
public abstract class ClassPathBuilderFactory {
    private ClassPathBuilderFactory () {
    }

    /**
     * Creates a class path builder that is able to compile the class path of the supplied
     * {@link Application application}.
     */
    public static ClassPathBuilder create (Application application)
    {
        return application.isUseCodeCache()
                ? new CacheBasedClassPathBuilder(application)
                        : new DefaultClassPathBuilder(application);
    }
}
