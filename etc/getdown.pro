#
# $Id$
#
# Proguard configuration file for Getdown launcher

-basedirectory ../

-injars dist/getdown.jar(!**/tools/**)
-injars dist/getdown.jar(**/tools/*Patcher*,**/tools/Differ*)
-injars dist/lib/jRegistryKey.jar(!META-INF/*)
-injars dist/lib/samskivert.jar(
  com/samskivert/Log.class,**/io/**,**/swing/**,**/net/**,**/text/**,**/util/**)
-injars dist/lib/commons-codec.jar(!META-INF/*)

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

-keep class * extends javax.swing.JApplet {
    <methods>;
}

-keep public class com.threerings.getdown.launcher.GetdownAppletConfig {
    *;
}
