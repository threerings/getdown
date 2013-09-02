# How to launch Getdown as an applet.

# Applet Mode

It is possible to run Getdown as a signed applet, which will then download and
launch your application. This can provide an "in the web browser" experience.
It is even possible to have Getdown invoke your application in the same JVM in
which Getdown is running, and allow the application to take over the Getdown
window, so that you can provide a very applet-like experience.

## Signing

Because Getdown must run as a signed applet, so that it has privileges to write
to the user's file system and launch a separate JVM, you must sign the
getdown-client.jar file with a
[code signing certificate](http://download.oracle.com/javase/1.4.2/docs/guide/plugin/developer_guide/rsa_signing.html).

As Getdown is a general purpose launching mechanism, it is also necessary to
sign your Getdown metadata with the same certificate. Otherwise Getdown could
be used by anyone to run arbitrary applications using your credentials. That
would be bad.

Note that the code signing certificate for Getdown must be an RSA certificate
rather than a DSA certificate, which is the default when generating test
certificates with `keytool`. Be sure to pass `-keyalg rsa` when generating test
certificates.

More details on signing will be provided below.

## Applet tag

Running Getdown as an applet requires creating an applet tag, which will
contain basic metadata, some of which is duplicated from the [[Getdown Dot Text|getdown.txt file]]
to allow the applet to start up more quickly and to provide a
pretty user interface.

Here is an example applet tag:

    <object height="253" width="424" type="application/x-java-applet;version=1.5"
            archive="http://myapp.com/client/getdown-client.jar">
      <param name="archive" value="http://myapp.com/client/getdown-client.jar"/>
      <param name="code" value="com.threerings.getdown.launcher.GetdownApplet"/>
      <param name="appname" value="myapp"/>
      <param name="appbase" value="http://myapp.com/client/"/>
      <param name="bgimage" value="http://myapp.com/client//background.png"/>
      <param name="errorbgimage" value="http://myapp.com/client/background_error.jpg"/>
      <param name="ui.status" value="30, 60, 364, 80"/>
      <param name="ui.status_text" value="FFEC48"/>
      <div class="nojava">You must first install Java to play this game in your browser!</div>
    </object>

Much of the applet tag is standard, including the attributes `width`, `height`,
`type`, `archive`, as well as the `archive` and `code` parameters. The
Getdown-specific parameters are:

### appname

This identifier is used to determine the name of the directory in which the
application data will be stored. This varies by operating system:

- Windows Vista: `%HOME%\AppData\LocalLow\myapp\`
- Windows XP, etc.: `%HOME%\Application Data\myapp\`
- Mac OS: `$HOME/Library/Application Support/myapp/`
- Linux: `$HOME/.getdown/myapp/`

### appbase

This is the same `appbase` configuration as the one in `getdown.txt`. It will
be used to download the `getdown.txt` file to obtain all other application
data.

### bgimage, errorbgimage

These should be fully qualified URLs that reference the same images in the
`ui.background` and `ui.error_background` configuration values from the
`getdown.txt` file. They are duplicated here so that the applet can immediately
display a branded user interface without having to first download the
`getdown.txt` file.

### ui.status, ui.status_text

These should contain the same values as those specified in the `getdown.txt`
file. These values are only used if there is a problem downloading the
`getdown.txt` file, at which point Getdown needs to display feedback to the
user and needs to know where on the `errorbgimage` to render that feedback.

### jvmargN and appargN

You can augment the `jvmarg` and `apparg` configuration in `getdown.txt` with
applet parameters (which can be dynamically generated when the user requests
the page that contains the `<applet>` tag).

To do so, simply add `jvmargN` or `appargN` `<param>` tags with increasing
values for `N`. For example:

    <object ...>
      <param name="jvmarg0" value="-Xmx256M"/>
      <param name="jvmarg1" value="-Dsun.java2d.opengl=true"/>
      <param name="apparg0" value="--username"/>
      <param name="apparg1" value="someusername"/>
    </object>

Note that the `jvmarg` and `apparg` configuration specified in the applet tag
will appear *after* the `jvmarg` and `apparg` configuration from the
`getdown.txt` file, on the command line.

## Signature File

In addition to signing the `getdown-client.jar` file with your code signing
certificate, you must generate a signature file which signs the contents of
your `digest.txt` file. This is accomplished at the same time as you generate
your `digest.txt` file. Simply supply the path to your keystore, its password
and the alias of the key to use for signing to the `<digest>` Ant task:

    <taskdef name="digest" classname="com.threerings.getdown.tools.DigesterTask"
             classpathref="getdown.classpath"/>
    <property file="certificate.properties"/>
    <digest appdir="${app_dir}" keystore="${sign.keystore}" storepass="${sign.storepass}"
            alias="${sign.alias}"/>

As you can see above, we recommend storing this sensitive metadata in a
separate properties file, to ensure that it does not get committed to version
control or leak to external parties. The `certificate.properties` file above
would contain data like:

    sign.keystore = /path/to/keystore.dat
    sign.storepass = s3cr3t
    sign.alias = mycompanyalias

This will generate a file `digest.txt.sig` which must be included in your
application deployment alongside the `digest.txt` file. The applet will
download this file to verify the contents of your `digest.txt` file and will
refuse the run the application if it cannot be found or if the signatures do
not match.

Because the `digest.txt` file contains cryptographic hashes of all of the code
and resources that make up your application, this means that it is impossible
for someone to use your signed `getdown-client.jar` file to do anything other
than run the exact application that you deployed with it.

Also note that you must generate a new `digest.txt.sig` every time you update
your application, along with the updated `digest.txt` file.

## Signing `getdown-client.jar`

Signing the `getdown-client.jar` file is easily accomplished using the
`signjar` Ant task (which is included with the standard Ant distribution). Here
is an example:

    <property file="certificate.properties"/>
    <signjar keystore="${sign.keystore}" alias="${sign.alias}" storepass="${sign.storepass}">
      <fileset dir="${app_dir}" includes="getdown-client.jar"/>
    </signjar>

We assume you have a `certificate.properties` file configured as described above.

## Ensuring User Has Java Plugin

Running Getdown as an applet is predicated on the user having at least Java 1.5
installed on their computer and properly configured as a browser plugin. The
applet tag we have shown above should trigger the process of installing the
Java plugin if the user does not already have it installed.

Note that we only require JDK 1.5 in our `<object>` tag above, you may wish to
require JDK 1.6, or you may want to use Getdown's support for running intself
in a 1.5 JDK and then automatically downloading and installing a 1.6 (or newer)
JDK as a part of the application download process. See [[Getdown Dot Txt|the
documentation on getdown.txt]] for details.