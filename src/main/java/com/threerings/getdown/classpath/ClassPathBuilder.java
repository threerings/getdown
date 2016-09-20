package com.threerings.getdown.classpath;

import java.io.IOException;

/**
 * A class path builder compiles the class path of the application to be launched.
 */
public interface ClassPathBuilder {
    /**
     * Builds and returns the class path of the application to be launched.
     */
    ClassPath buildClassPath () throws IOException;
}
