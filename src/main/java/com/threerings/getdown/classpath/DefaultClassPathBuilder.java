package com.threerings.getdown.classpath;

import java.util.LinkedHashSet;

import com.threerings.getdown.data.Application;
import com.threerings.getdown.data.Resource;

/**
 * The default class path builder assembles the class path from the code resources to be found in
 * the application directory.
 */
public class DefaultClassPathBuilder extends ClassPathBuilderBase
{
    public DefaultClassPathBuilder(Application _application) {
        super(_application);
    }

    @Override
    public ClassPath buildClassPath ()
    {
        LinkedHashSet<ClassPathElement> classPathEntries = new LinkedHashSet<ClassPathElement>();

        for (Resource resource: _application.getActiveCodeResources()) {
            classPathEntries.add(new ClassPathElement(resource.getFinalTarget()));
        }

        return new ClassPath(classPathEntries);
    }

}
