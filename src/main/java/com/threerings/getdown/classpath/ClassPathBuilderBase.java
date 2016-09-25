package com.threerings.getdown.classpath;

import com.threerings.getdown.data.Application;

/**
 * Base class for {@link ClassPathBuilder class path builders}.
 */
abstract class ClassPathBuilderBase implements ClassPathBuilder {
    public ClassPathBuilderBase(Application _application) {
        this._application = _application;
    }

    protected final Application _application;
}
