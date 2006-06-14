#
# $Id$
#
# Proguard configuration file for Getdown launcher

-basedirectory ../

-injars dist/getdown.jar(!**/tools/DigesterTask*,!**/tools/Differ*)
-injars lib/jRegistryKey.jar(!META-INF/*)
-injars lib/samskivert.jar(!META-INF/*,!**/velocity/**,!**/xml/**)
-injars lib/commons-io.jar(!META-INF/*)

-libraryjars <java.home>/lib/rt.jar
-dontskipnonpubliclibraryclasses

-outjars dist/getdown-pro.jar
-printseeds dist/proguard.seeds
-printmapping dist/proguard.map

-keep public class ca.beq.util.win32.registry.** {
    *;
}

-keep public class com.threerings.getdown.launcher.Getdown {
    public static void main (java.lang.String[]);
}

-keep public class com.threerings.getdown.launcher.GetdownApp {
    public static void main (java.lang.String[]);
}

-keep public class com.threerings.getdown.launcher.GetdownApplet {
    *;
}
