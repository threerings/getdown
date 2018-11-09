## What is it?

Getdown (yes, it's the funky stuff) aims to provide a system for downloading and installing a
collection of files on a user's machine and upgrading those files as needed. Though just any
collection of files would do, Getdown is mainly intended for the distribution and maintenance of
the collection of files that make up a Java application.

It was designed as a replacement for [Java Web Start](http://java.sun.com/products/javawebstart/)
due to limitations in Java Web Start's architecture which are outlined in the
[rationale](https://github.com/threerings/getdown/wiki/Rationale) section.

Note: Getdown was designed *in 2004* as an alternative to Java Web Start, because of design choices
made by JWS that were problematic to the use cases its authors had. It is _not_ a drop-in
replacement for JWS, aimed to help the developers left in the lurch by the deprecation of JWS in
Java 9. It may still be a viable alternative for developers looking to replace JWS, but don't
expect to find feature parity with JWS.

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

  * [Puzzle Pirates](https://www.puzzlepirates.com/) - OOO
  * [Spiral Knights](https://www.spiralknights.com/) - OOO

Getdown is implemented in Java, and is designed to deploy and update JVM-based applications. While
it would be technically feasible to use Getdown to deploy non-JVM-based applications, it is not
currently supported and it is unlikely that the overhead of bundling a JVM just to run Getdown
would be worth it if the JVM were not also being used to run the target application.

## Release notes

See [CHANGELOG.md](CHANGELOG.md) for release notes.

## Obtaining Getdown

Getdown will likely need to be integrated into your build, for which we have
[separate instructions](https://github.com/threerings/getdown/wiki/Build-Integration). You can
also download the individual jar files from Maven Central if needed:

  * In [this Maven Central directory](http://repo2.maven.org/maven2/com/threerings/getdown) you can
    find the latest versions of `getdown-launcher.jar` (the code that updates and launches your
    app), `getdown-ant.jar` (build integration for the Ant build tool, and which can also be used
    with Maven), and `getdown-core.jar` (the core updating and launching logic which you don't
    usually use directly, unless you're embedding Getdown in your app).

You can also:

  * [Check out the code](https://github.com/threerings/getdown) and build it yourself.
  * Browse the [source code] online.
  * View the [javadoc documentation] online.

## JVM Version Requirements

  * Getdown version 1.8.x requires Java 7 VM or newer.
  * Getdown version 1.7.x requires Java 7 VM or newer.
  * Getdown version 1.6.x requires Java 6 VM or newer.
  * Getdown version 1.5 and earlier requires Java 5 VM or newer.

## Migrating from Getdown 1.7 to Getdown 1.8

See [this document](https://github.com/threerings/getdown/wiki/Migrate17to18) on the changes needed
to migrate from Getdown 1.7 to 1.8.

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
