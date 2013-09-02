# Using explicitly controlled versions of your app

# Versioned Mode

By default Getdown runs in "versionless" mode, in which case it attempts to download the
`digest.txt` file every time the app is launched, and if it detects that the app has been updated,
it processes the update before launching the app.

This is fine for apps for which upgrading to the latest code is not essential, and apps that are
not concerned about minimizing the data downloaded between app updates. But Getdown was designed to
deploy client updates to MMOs which have the property that the client and server must be in sync
(thus a client must upgrade when an update is available), and in which thousands of clients are all
told to update simultaneously, so we have strong incentive to minimize the amount of data
downloaded for an update.

For apps with such requirements, Getdown supports a "versioned" mode, in which a numeric version is
assigned to a particular build of the client, new builds have (higher) versions, and patch files
are generated that will bring the client from version X to version X+N.

## Version Components

We will use the [Spiral Knights](http://www.spiralknights.com/) client as an example to describe the
way a versioned app works. Thus all the URLs in this documentation are real URLs that can be
fetched and inspected for clarifying details (at least for the next six months or so).

The `getdown.txt` of a versioned app will have metadata like the following at its start:

    version = 20130212182129
    appbase = http://download.threerings.net/spiral/%VERSION%
    latest = http://download.threerings.net/spiral/client/getdown.txt

The `version` defines a `long` integer value which must monotonically increase. A simple technique
for generating monotonically increasing versions is to use
[SimpleDateFormat](http://docs.oracle.com/javase/7/docs/api/java/text/SimpleDateFormat.html) (or
something similar) with a formatting string of `yyyyMMddHHmmss". That is how the example version is
generated.

The `appbase` URL then contains `%VERSION%` which is replaced with the actual version of the app
being downloaded. All resources for this version of the app are hosted in that directory. In the
case of the above example, all the resources for the `20130212182129` version are hosted at:

    http://download.threerings.net/spiral/20130212182129

For example:

    http://download.threerings.net/spiral/20130212182129/getdown.txt
    http://download.threerings.net/spiral/20130212182129/digest.txt
    http://download.threerings.net/spiral/20130212182129/code/config.jar
    # etc.

The reason for substituting the version into the URL is to ensure that all of the assets for a
particular version are loaded from unique URLs. A versioned app will never fail to download and
install because some intermediate entity is caching the contents of a URL from some previous
version and preventing a client from getting the latest data.

The final configuration, `latest`, will be described in the next section.

## Determining Latest Version

Getdown provides two mechanisms for determining the latest version of your app. The first is a
convenience that streamlines the user experience and the second is a fallback mechanism to ensures
that your app will be updated when necessary, regardless of whether or not the first mechanism was
able to succeed.

The first mechanism is to use the (optional) `latest` URL provided in your `getdown.txt`. This
points to a URL which always contains the `getdown.txt` file for the latest version of your app.
Because this URL does not change when your app is updated (the contents do, but the URL itself
remains the same), there is a possibility that a client will see out of date data when downloading
this URL. Even if you provide the proper cache control headers, there are (or at least have been)
ISPs that aggressively cache the contents of URLs regardless of whether their HTTP headers indicate
that they should not be cached.

To maximize the likelihood that the `latest` URL mechanism will work, your web server should
add:

    Cache-control: no-cache

to the HTTP headers returned when fetching the `latest` URL.

In addition to the `latest` URL, Getdown also looks for a file named `version.txt` in `appdir`,
which contains (in ASCII text) the latest version of the app. For example:

    20130212182129

This file can be created by your app if it discovers that its code is out of date. For example, if
a network client attempts to log onto a server and discovers that its code is out of date, the
server can communicate the latest version of the code to the client and the client can write that
version into `version.txt` and relaunch Getdown which will trigger an app update. This mechanism
ensures that there is always some last resort mechanism for updating the app, even if unintentional
caching prevents Getdown from discovering the latest version of the app via the `latest` URL.

Note that if your app is allowed to run in *offline mode*, Getdown will delete the `version.txt`
file once it has used it to update to the version specified in that file.

Getdown includes code that aids in this last-resort update process. See `updateVersionAndRelaunch`
in [LaunchUtil](http://getdown.googlecode.com/svn/apidocs/com/threerings/getdown/util/LaunchUtil.html).

## Using Patch Files

When upgrading from version M to version N, Getdown will first look for a patch file that contains
just the differences between those two versions. If this patch file exists, it will download and
apply it before falling back to its standard process of computing the MD5 hash of every file
included in the app and redownloading any that fail to hash to the correct value. Thus when a valid
patch file is found, the hash checking phase will not find any files that have an incorrect hash
and the app will launch immediately after applying the patch file. If a patch file cannot be found
or is somehow corrupted, Getdown will always fall back to the file-by-file mechanism for updating
the app, to ensure that the user is never left with a non-working install of your app.

Patch files must follow a specific naming convention. If one were running version 20130129205203 of
Spiral Knights (described above) and the latest version is 20130212182129, Getdown will seek a
patch file at the following URL:

    http://download.threerings.net/spiral/20130212182129/patch20130129205203.dat

When creating an updated version of your app, you can generate patch files against some number of
recent versions and make them available. Getdown will not apply multiple patch files. If it does
not find a patch file from the current version of the app installed on the user's computer to the
latest version of the app, it will fall back to file-by-file updating. There is no way for Getdown
to discover intermediate versions and attempt to apply successive patch files, nor is that
guaranteed to be a good idea as it could end up downloading more data than the file-by-file
approach if the same resource changes repeatedly from version to version.

Patch files are generated using `com.threerings.getdown.tools.Differ`. Patch files are in `JarDiff`
format, but the Getdown jar diff generator is more robust than the one included with Java Web
Start. Assuming you have three versions of your app in directories named `1`, `2` and `3` (where 3
is the latest version), you can generate patch files from version 1 to 3 and version 2 to 3 like
so:

    java -classpath getdown-1.x.jar com.threerings.getdown.tools.Differ 3 1
    java -classpath getdown-1.x.jar com.threerings.getdown.tools.Differ 3 2

The patch files will be placed into the `3` directory with the correct name. This process can
easily be integrated into an Ant build script (or a Maven build using the Maven Ant plugin) using
standard Ant mechanisms for invoking Java processes. The generated patch files should be uploaded
to your web server with the latest version of your app.

Note that if you use auxiliary resource bundles, separate patch files will be generated for each
auxiliary resource group, and Getdown will automatically take care of downloading the patch files
for all installed auxiliary resource groups during the normal patching process. Just be sure to
include `patch*` files generated by `Differ` when uploading your app.

## The "Current" Version

When using Getdown in versioned mode, it is useful to have a copy of the latest version of
`getdown.txt` at a well-known URL which you can use in your installer scripts and which you can use
in the `latest` configuration of your `getdown.txt` file. Spiral Knights uses `client`, but you can
use any well known identifier you like. This is also a useful place to put custom JVMs and any
other data that does not change from version to version of your app. For example, the Spiral
Knights web servers are structured like so:

    .../client/getdown.txt        # always contains the getdown.txt for latest version of app
    .../client/spiral-install.exe # windows installer
    .../client/spiral-install.dmg # mac os installer
    .../client/spiral-install.bin # linux installer
    .../client/java_linux.jar     # up to date JVM for install on Linux
    .../client/java_windows.jar   # up to date JVM for install on Windows
    
    .../20130212182129/getdown.txt             # getdown.txt for version 20130212182129 of game
    .../20130212182129/patch20130129205203.dat # getdown.txt for upgrading 20130129205203 to 20130212182129
    ..etc..
    
    .../20130129205203/getdown.txt # getdown.txt for version 20130129205203 of game
    ..etc..

An arrangement like this will likely prove useful for your app as well.