#
# $Id$
#
# Proguard configuration file for Getdown launcher

-injars dist/getdown-retro.jar(!**/tools/DigesterTask*,!**/tools/Differ*)
-injars lib/jRegistryKey.jar(!META-INF/*)
-injars lib/samskivert.jar(!META-INF/*,!**/velocity/**,!**/xml/**)
-injars lib/commons-io.jar(!META-INF/*)
-injars lib/retroweaver-rt-1.2.2.jar(!META-INF/*)

-libraryjars <java.home>/lib/rt.jar

-outjars dist/getdown-pro.jar
-printseeds dist/proguard.seeds
-printmapping dist/proguard.map

-keep public class ca.beq.util.win32.registry.** {
    *;
}

-keep public class com.threerings.getdown.launcher.Getdown {
    public static void main (java.lang.String[]);
}
