#
# $Id$
#
# Proguard configuration file for Getdown launcher

-injars dist/getdown.jar(!**/tools/DigesterTask*,!**/tools/Differ*)
-injars lib/jRegistryKey.jar
-injars lib/samskivert.jar(!**/velocity/**,!**/xml/**)
-injars lib/commons-io.jar

-libraryjars <java.home>/lib/rt.jar

-outjars dist/getdown-dop.jar

-keep public class ca.beq.util.win32.registry.** {
    public protected *;
}

-keep public class com.threerings.getdown.launcher.Getdown {
    public static void main (java.lang.String[]);
}
