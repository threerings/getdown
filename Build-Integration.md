# Integrating Getdown into your build.

# Ant Integration

As described in the [[Quick Start|quick start]] documentation, it is necessary to
invoke the digester to create a `digest.txt` file when building your
application deployment.

Getdown provides an Ant task to simplify this process for projects that make
use of the Ant build system.

We will assume that you have an Ant target that prepares your application
deployment directory by copying in the `getdown.txt` file and your
application's jar files and any other resources that are needed. We'll also
assume that directory is referenced by `${app_build_dir}` in your Ant file.

## Manual Dependencies

If you manage your dependencies manually, you can simply download
`getdown-tools.jar` from the
[downloads page](http://code.google.com/p/getdown/downloads/list) and put it
somewhere accessible to your build system.

Then use the following Ant code to create your digest file:

    <taskdef name="digest" classname="com.threerings.getdown.tools.DigesterTask"
             classpath="path/to/getdown-tools.jar"/>
    <digest appdir="${app_build_dir}"/>

## Maven Ant Tasks

If you use the Maven Ant tasks to obtain dependencies for your build, you can
avoid downloading the `getdown-tools.jar` manually, and instead obtain Getdown
via Maven Central. This is done as follows:

    <artifact:dependencies pathId="getdown.classpath">
      <dependency groupId="com.threerings" artifactId="getdown" version="1.2"/>
    </artifact:dependencies>
    <taskdef name="digest" classname="com.threerings.getdown.tools.DigesterTask"
             classpathref="getdown.classpath"/>
    <digest appdir="${app_build_dir}"/>

# Maven Integration

A [Maven plugin](https://bitbucket.org/joxley/getdown-maven-plugin) exists for
generating the `digest.txt` from a Maven build.

You must ensure that a `getdown.txt` file is copied to the `target` (or
specified `appdir`) directory during the build process. Then setup the plugin
as shown below:

    <plugin>
      <groupId>org.bitbucket.joxley</groupId>
      <artifactId>getdown-maven-plugin</artifactId>
      <version>0.0.1</version>
      <configuration>
        <appdir>target/app</appdir> <!-- Defaults to target -->
      </configuration>
      <executions>
        <execution>
          <phase>package</phase>
          <goals>
            <goal>digest</goal>
          </goals>
        </execution>
      </executions>
    </plugin>