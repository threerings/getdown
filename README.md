## What is it?

Getdown (yes, it's the funky stuff) is a system for deploying Java applications to end-user
computers, as well as keeping those applications up to date.

It was designed as a replacement for [Java Web Start](https://docs.oracle.com/javase/8/docs/technotes/guides/javaws/)
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

Note that because one can not rely on users having a JRE installed, you must create a custom
installer for each platform that you plan to support (Windows, macOS, Linux) that installs a JRE,
the Getdown launcher jar file, a stub configuration file that identifies the URL at which your real
app manifest is hosted, and whatever the appropiate "desktop integration" is that provides an icon
the user can click on. We have some details on the
[installers](https://github.com/threerings/getdown/wiki/Installers) documentation page, though it
is unfortunately not very detailed.

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

Getdown will likely need to be integrated into your build. We have separate instructions for
[build integration]. You can also download the individual jar files from Maven Central if needed.
Getdown is comprised of three Maven artifacts (jar files), though you probably only need the first
one:

  * [getdown-launcher](http://repo2.maven.org/maven2/com/threerings/getdown/getdown-launcher)
    contains minified (via Proguard) code that you actually run to update and launch your app. It
    also contains the tools needed to build a Getdown app distribution.

  * [getdown-core](http://repo2.maven.org/maven2/com/threerings/getdown/getdown-core) contains the
    core logic for downloading, verifying, patching and launching an app as well as the core logic
    for creating an app distribution. It does not contain any user interface code. You would only
    use this artifact if you were planning to integrate Getdown directly into your app.

  * [getdown-ant](http://repo2.maven.org/maven2/com/threerings/getdown/getdown-ant) contains an Ant
    task for building a Getdown app distribution. See the [build integration] instructions for
    details.

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

See [this document](https://github.com/threerings/getdown/wiki/Migrating-from-1.7-to-1.8) on the
changes needed to migrate from Getdown 1.7 to 1.8.

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
[build integration]: https://github.com/threerings/getdown/wiki/Build-Integration
