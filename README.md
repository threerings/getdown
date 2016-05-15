## What is it?

Getdown (yes, it's the funky stuff) aims to provide a system for downloading and installing a
collection of files on a user's machine and upgrading those files as needed. Though just any
collection of files would do, Getdown is mainly intended for the distribution and maintenance of
the collection of files that make up a Java application.

It was designed as a replacement for [Java Web Start](http://java.sun.com/products/javawebstart/)
due to limitations in Java Web Start's architecture which are outlined in the
[rationale](https://github.com/threerings/getdown/wiki/Rationale) section.

## How do I use it?

A tutorial and more detailed specification are available from the [Documentation] page. Questions
can be posted to the [OOO Libs Google group].

## How does it work?

The main design and operation of Getdown is detailed on the
[design](https://github.com/threerings/getdown/wiki/Design) page. You can also browse the
[javadoc documentation] and [source code] if you're interested in implementation details.

## Where can I see it in action?

Getdown was originally written by developers at [OOO] for the deployment of their Java-based
massively multiplayer games. Try out any of the following games to see it in action:

  * [Puzzle Pirates](http://www.puzzlepirates.com/) - OOO
  * [Bang! Howdy](http://banghowdy.com) - OOO
  * [Spiral Knights](http://spiralknights.com) - OOO
  * [Tribal Trouble 2](http://www.tribaltrouble2.com/) - Oddlabs

Getdown is implemented in Java, but certainly can be used to deploy non-Java-based applications.
Doing so would be a little crazy since you may have to install a JVM on the user's machine (if they
don't already have one installed), which is a ~7MB download. This isn't so bad if you're already
installing a Java application and must have a JVM, but it's a little crazy if the JVM is only used
for your installer. It is probably possible to compile Getdown with
[GCJ](http://gcc.gnu.org/java/), which would make Getdown a viable choice for non-Java
applications.

## Obtaining Getdown

The latest version of Getdown can be obtained thusly:

  * Download the pre-built jar file from Maven Central:
    [getdown-1.5.jar](http://repo2.maven.org/maven2/com/threerings/getdown/1.5/getdown-1.5.jar)
  * Obtain the jar artifact via Maven with the following identifier: `com.threerings:getdown:1.5`.
  * [Check out the code](https://github.com/threerings/getdown) and build it yourself.

You can also:

  * View the [javadoc documentation] online.
  * Browse the [source code] online.

## Building

Getdown is built with Maven in the standard ways. Invoke the following commands, for fun and
profit:

```
% mvn compile  # builds the classes
% mvn test     # builds and runs the unit tests
% mvn package  # builds and creates jar file
% mvn install  # builds, jars and installs in your local Maven repository
```

## Discussion

Feel free to pop over to the [OOO Libs Google Group] to ask questions and get (and give) answers.

[Documentation]: https://github.com/threerings/getdown/wiki
[OOO Libs Google group]: http://groups.google.com/group/ooo-libs
[source code]: https://github.com/threerings/getdown/tree/master/src/main/java/com/threerings/getdown/launcher
[javadoc documentation]: https://threerings.github.com/getdown/apidocs/
[OOO]: https://en.wikipedia.org/wiki/Three_Rings_Design
