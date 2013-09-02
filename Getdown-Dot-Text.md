# An anatomy of the getdown.txt file.

# `getdown.txt`

The `getdown.txt` file contains all of the configuration information used by
Getdown to control the downloading, installing, patching and execution of your
application. Herein we describe all of the configuration options available.

Note that unless specifically annotated with "(required)" these configuration
directives are optional.

## Comments

Comments start with the `#` character and continue to the end of the line. Any
text in a comment is ignored by Getdown.

## Metadata Configuration

### appbase (required)

This defines the URL at which your application's data can be downloaded. It is
simply a URL which serves as a prefix to all of the other files in your
`getdown.txt` file.

For example, if your `appbase` was `http://myapp.com/myapp/` and your
`getdown.txt` file contained a code resource `code = code/myapp.jar`, Getdown
will attempt to download that code resource at the URL
`http://myapp.com/myapp/code/myapp.jar`.

This URL can also contain a __version token__ which is used when Getdown is
running in __[[Versioned Mode|versioned mode]]__. Such a URL looks like this:
`http://myapp.com/myapp/%VERSION%/`.

### version

When Getdown is running in __[[Versioned Mode|versioned mode]]__, this contains
the version number of this particular application installation. This should be
a simply 64-bit integer for which larger numbers means newer versions. A common
approach is to generate the version at build time using a timestamp of the form
`YYYYMMDDHHMMSS`.

### allow_offline

By default, Getdown will fail if it is running a non-versioned application and
cannot contact the server configured in `appbase` to check for updates. If you
add `allow_offline = true` to your `getdown.txt`, Getdown will ignore such
failures and allow the application to be run anyway.

## Code and Resource Configuration

### code

The `code` directive tells Getdown about a `jar` file to be downloaded and to
be placed on the classpath when your application is run. You can have as many
code directives as desired. For example:

    code = application.jar
    code = sharedlib.jar
    code = extras/extracode.jar

If you have no `code` directives, the only classes on the classpath will be the
Java standard libraries. Generally at least one `code` directive is needed to
accomplish anything interesting.

### resource

The `resource` directive tells Getdown about a resource file that is needed by
your application, but which should not be placed on the classpath. This can be
used for media needed by Getdown itself (see `ui.background` below for an
example), or for media that your application will access directly via the
filesystem rather than via the ClassLoader (for performance reasons).

For applications that are very sensitive to resource loading performance
(Getdown was designed to deploy games, after all), it is also possible to
instruct Getdown to unpack resource files, so that they can be loaded directly,
rather than loading them from `.jar` files, which incurs some decompression
overhead (and prevents the use of `mmap` to map files directly into memory from
the filesystem). Unpacking resources currently only works for `.jar` files.

To request that a resource be unpacked once it has been downloaded and its
checksum verified, use the `uresource` directive. For example:

    resource = wontbeunpacked.jar
    uresource = willbeunpacked.jar

The resource is unpacked in the directory that contains the `.jar` file that is
being unpacked. For example, if you have a resource `tounpack.jar` which
contains the following files: `filea.jpg`, `subdir/fileb.jpg`, and you have a
`getdown.txt` with the following configuration:

    uresource = rsrc/tounpack.jar

Then, after unpacking, you will end up with a directory structure like so:

    rsrc/tounpack.jar
    rsrc/filea.jpg
    rsrc/subdir/fileb.jpg

Note also that when Getdown updates an unpacked resource, it simply deletes the
original `.jar` file, downloads the new `.jar` file and unpacks the new file,
overwriting files in the old resource jar file. No care is taken to explicitly
delete old resources that do not exist in the new resource file. As such,
resource unpacking should be used with care. Motivated users are welcome to
submit a patch to improve the management of unpacked resources.

## User Interface Configuration

The various interface configuration parameters described below are shown on
this annotated image of Getdown in operation:

[### ui.name (required)

This specifies the name of your application and is displayed in the title bar
of the Getdown window as it downloads and installs your application, as well as
other places where the operating system wants a name to be displayed.

### ui.background

This specifies an image resource which will be displayed as the background of
the window shown by Getdown while it is downloading and installing your
application.

The dimensions of your background image will dictate the dimensions of the
Getdown window. The window will be sized to exactly fit your background image.
The pixel positions supplied in the `ui.progress` and `ui.status` configuration
(desribed below) should assume a window with size equal to the size of your
background image.

Because Getdown will use this image resource immediately, before it has
downloaded any of your application's data, it is common to include this file
with the application's installer so that the user has a properly polished
initial user experience. However, it is also useful to add the background image
to your `getdown.txt` file as a `resource` so that you can update the
background image easily along with the rest of your application. Getdown will
have no problem with such "pre-supplied" resources, and if the MD5 hash of the
background image supplied with the installer matches the hash of the background
image supplied with the application, it won't even redownload the background
image when downloading the application.

### ui.error_background

If specified, the `ui.background` image will be replaced with this image in the
event that an error has occurred during download, installation or launch. Like
`ui.background`, this image should be supplied with the application installer
as well as as a `resource`, and the status and progress indicators (described
below) will be rendered in the same location atop this error image as they are
atop the normal image.

### ui.icon

This configures an icon image to be used by the Getdown application launcher.
Like `ui.background`, the referenced image should be supplied with your
installer and as a resource. The image will be loaded and supplied to a call to
[http://download.oracle.com/javase/6/docs/api/java/awt/Frame.html#setIconImage%28java.awt.Image%29 Frame.setIconImage](http://getdown.googlecode.com/svn/wiki/ui_diagram.png]).

Multiple images may also be supplied, and they will be supplied, in order, to a
call to
[Window.setIconImages](http://download.oracle.com/javase/6/docs/api/java/awt/Window.html#setIconImages%28java.util.List%29).
The order of the images in the `getdown.txt` file will
dictate the order of the images passed to the `setIconImages` call. For
example:

    ui.icon = std_icon.png
    ui.icon = big_icon.png

### ui.progress

Getdown displays both a progress bar and a textual display of download
progress. They are superimposed over one another. The `ui.progress`
configuration specifies the dimensions of the rectangle in which this progress
display takes place. The dimensions of the rectangle are relative to the
upper-left corner of the Getdown window. For example:

    ui.progress = 17, 321, 458, 22

describes a progress rectangle that is 17 pixels from the left of the window,
321 pixels down from the top of the window, 458 pixels wide and 22 pixels tall.

The progress bar will be rendered to fill the entire progress rectangle (with a
width that represents the percentage completion of the current task). The
progress text will be rendered centered, horizontally and vertically in the
specified rectangle.

### ui.progress_bar

This configures the color of the progress bar. Note that text will be rendered
in the `ui.progress_text` color, and thus these two colors should be selected
so as to provide reasonable contrast. Note also that if `ui.progress_image` is
specified, the `ui.progress_bar` color is not used.

The color should be specified as a hexadecimal RGB value (with no leading `#`).
For example:

    ui.progress_bar = FF3333

### ui.progress_text

This configures the color of the text rendered atop the progress bar. The color
should be specified as a hexadecimal RGB value (with no leading `#`). For
example:

    ui.progress_text = FFFFFF

### ui.progress_image

This defines an image that will be rendered as the progress bar, instead of
using a solid color. If this image is specified, the `ui.progress_bar` will not
be used. The image will be cropped at a width that matches the current
percentage completion to convey the progressively increasing progress. Thus the
image is slowly "unveiled," from left to right, as the task completes.

This setting should reference an image, most likely both shipped with your
installer and referenced via a `resource` directive (see `ui.background` for
details). For example:

    ui.progress_image = progress.png
    resource = progress.png

### ui.status

Getdown displays textual feedback during the download and installation process.
This setting configures the rectangle in which that status is displayed. The
dimensions of the rectangle are pixel coordinats, relative to the upper-left
corner of the Getdown window. For example:

    # Status rectangle: x=57 y=254 width=373 height=68
    ui.status = 57, 245, 373, 68

The status is displayed left-justified in the supplied rectangle, and flush
with the bottom of the rectangle. The expectation is that your branding imagery
is displayed above the status text. The status text is generally only a single
line, but in the case of errors, it can become longer and the text will be line
wrapped, and only then will spill upwards, potentially covering your branding
image.

### ui.status_text

This configures the color of the text rendered in the status region. The color
should be specified as a hexadecimal RGB value (with no leading `#`). For
example:

    ui.status_text = FFFFFF

### ui.install_error

This should contain a URL that will be shown to the user if an unrecoverable
error occurs during download, installation or launch. Getdown makes every
effort to automatically recover from any errors, but if it is unable to
recover, it will display a message instructing the user to visit the URL
specified by `ui.install_error`.

## Launcher Configuration

Getdown launches your application in a separate JVM, and allows for
fine-grained control over the configuration of that JVM. The following
configuration options relate to the configuration and launch of your
application.

### class (required)

This specifies the main entry point of your application (the class which
contains a `main` method, which is to be invoked to start your application). It
should be a fully qualified Java class name. For example:

    class = com.threerings.bang.client.BangApp

The specified class should exist in one of the jar files configured with the
`code` directive.

### jvmarg

This supplies arguments to the JVM. Multiple `jvmarg` directives may be used.
These can be used to configure the heap size, set system properties, or
anything else that is needed. For example:

    jvmarg = -Xmx256M
    jvmarg = -Djava.library.path=%APPDIR%/native
    jvmarg = -Dappdir=%APPDIR%
    jvmarg = -Dversion=%VERSION%

Note that `%APPDIR%` will be replaced with the full path to the application
install directory and `%VERSION%` will be replaced with the current version, if
the application is running in versioned mode.

### apparg

This supplies arguments to the application. Multiple `apparg` directives may be
used. The values supplied to apparg will be passed, in order, to the
application when it is launched. For example:

    apparg = --appdir
    apparg = %APPDIR%
    apparg = --version
    apparg = %VERSION%

Note that `%APPDIR%` will be replaced with the full path to the application
install directory and `%VERSION%` will be replaced with the current version, if
the application is running in versioned mode.

### Custom Java Installation

Getdown will run on a JVM from version 1.5 onward. It can be made to run on
even earlier versions of the JVM, but those are so rare these days that it's
probably not worth your trouble. However, your application may require a newer
version of the JVM, to work around bugs, or take advantage of new features. As
such, it is possible to instruct Getdown to download and install a private JVM
for use when running your application.

You are required to package the JVMs for the platforms you wish to support into
`.jar` files that have the same structure as an installed version of that JVM.
Examples of such `.jar` files can be seen
[for Windows](http://s3download.banghowdy.com/client/java_windows.jar) and
[for Linux](http://s3download.banghowdy.com/client/java_linux.jar).

An example configuration for custom JVM installation is as follows:

    java_version = 1050006
    java_location = [windows] /client/java_windows.jar
    java_location = [linux] /client/java_linux.jar

The `java_version` directive specifies the minimum version of JVM needed. This
version is obtained from the output of `System.getProperty("java.version")` in
the following way. `java.version` is of the form `MAJ.MIN.REV_PATCH`. A numeric
value is computed that is equal to:

    PATCH + 100 * (REV + 100 * (MIN + 100 * MAJ))

Thus the above configuration is specifying a requirement for Java
version `1.5.0_06` or newer.

If the JVM used to invoke Getdown is not of sufficiently high version, it will
attempt to download the JVM specified by the `java_location` directive. As
shown above, this should include platform-specific alternatives (see below for
details on platform-specific directives) for each platform for which a JVM is
available for download.

One can also specify that an exact version of the JVM is required, and Getdown
will attempt to download a JVM if the installed JVM does not exactly match the
version in `java_version`. This behavior is activated via:

    java_exact_version_required = true

Note that in the above configuration, we are taking advantage of a property of
URL composition. The full URL to the JVM will be computed by combining the
value of `java_location` with the value of `appbase`. However, in this case
`java_location` is an absolute path (it starts with a `/`) which means that any
path component of the `appbase` will be ignored. Thus we may still have a
versioned `appbase` without having to replicate our JVMs every time we make a
release of our application. For example:

    appbase = http://s3download.banghowdy.com/bang/client/%VERSION%
    java_version = 1050006
    java_location = [windows] /client/java_windows.jar
    java_location = [linux] /client/java_linux.jar

will yield the following URL for downloading the JVM on Linux:

    http://s3download.banghowdy.com/client/java_linux.jar

### Alternative Entry Points

It is possible to ship multiple applications in a single Getdown installation,
and select the entry point based on a command line argument supplied to
Getdown. The usage is as follows:

    java -jar getdown-client.jar app_dir [app_id]

The identifier supplied for `app_id` will be used to select the application
entry point `app_id.class` instead of `class`. For example:

    class = foo.bar.MainApp          # the default entry point
    editor.class = foo.bar.EditorApp # the entry point used for app id 'editor'

## Platform-specific Configuration

Getdown supports custom configuration on a per-platform basis. This can be used
to download native libraries for a particular platform (avoiding downloading
the resources for other platforms), or to supply custom JVM arguments for
particular platforms, or for anything else you might dream up.

Any configuration option can be marked as being platform-specific, and it will
only be processed if Getdown is running on that platform. Marking an option is
done as follows:

    option = [platform] data
    option = [platform-architecture] data
    option = [!platform] data
    option = [!platform-architecture] data

For example, to specify native library resources for the [LWJGL](http://lwjgl.org) OpenGL bindings, one would supply the following:

    resource = [linux] native/liblwjgl64.so
    resource = [linux] native/liblwjgl.so
    resource = [linux] native/libopenal.so
    resource = [linux] native/libopenal64.so
    resource = [windows] native/lwjgl.dll
    resource = [windows] native/lwjgl64.dll
    resource = [windows] native/OpenAL32.dll
    resource = [windows] native/OpenAL64.dll
    resource = [mac os x] native/liblwjgl.jnilib
    resource = [mac os x] native/openal.dylib
    jvmarg = -Djava.library.path=%APPDIR%/native

The above configuration uses only the platform identifier, returned by
`System.getProperty("os.name")`. It is also possible to use the architecture
identifier, returned by `System.getProperty("os.arch")`. This is done by
following the platform identifier with a `-` and then adding the desired
architecture. For example:

    resource = [linux-x86_64] native/liblwjgl64.so
    resource = [linux-i386] native/liblwjgl.so
    resource = [linux-i386] native/libopenal.so
    resource = [linux-x86_64] native/libopenal64.so
    resource = [windows-i386] native/lwjgl.dll
    resource = [windows-x86_64] native/lwjgl64.dll
    resource = [windows-i386] native/OpenAL32.dll
    resource = [windows-x86_64] native/OpenAL64.dll
    resource = [mac os x] native/liblwjgl.jnilib
    resource = [mac os x] native/openal.dylib
    jvmarg = -Djava.library.path=%APPDIR%/native

Note that omitting the architecture specifier means that the configuration will
be used for all architectures on the specified platform.

## Tracking

Getdown can be configured to ping tracking URLs during the download and
installation process. This allows the application developer to track the degree
to which users are completing their installations, to determine if they are
being deterred by large downloads, or at other points during application
installation.

Details TBD. Example:

    tracking_url = http://mytracker.net/some/tracking/prefix
    tracking_url_suffix=some/tracking/suffix
    tracking_percents = 5, 25, 50, 75, 99
    tracking_cookie_name=UID
    tracking_cookie_property=app.csid

## Auxiliary Resources

Getdown can be configured to defer the download of some of an application's
resources until such point as the application instructs Getdown to fetch those
resources. This allows an application developer to provide a smaller initial
download, and only trigger the download of additional resources when they are
needed.

Note that this system is currently very primitive and does not provide an ideal
user experience. Resources are not downloaded in the background, and triggering
the download requires a restart of the application.

Details TBD. Example:

    auxgroups = indian_post
    indian_post.code = indian_post.jar
    indian_post.uresource = rsrc/bonuses/indian_post/bundle.jar
    indian_post.uresource = rsrc/bounties/indian_post/bundle.jar

## Complete Examples

All of the above directives can be seen in action in the complete examples
offered by the [Puzzle Pirates](http://www.puzzlepirates.com/) and
[Bang! Howdy](http://www.banghowdy.com/) massively multiplayer online game
clients.

- [Puzzle Pirates getdown.txt](http://download.threerings.net/yoclient/client/getdown.txt)
- [Bang! Howdy getdown.txt](http://download.threerings.net/bang/client/getdown.txt)