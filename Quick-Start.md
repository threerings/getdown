# A quick introduction to Getdown

# Getdown Quick Start

Here we'll walk you through the basic structure of a Getdown project and the
steps needed to create and serve it.

## Metafiles

A Getdown project uses two metafiles: `getdown.txt` and `digest.txt`. The
`getdown.txt` file you create yourself (we'll explain that in a moment), and
the `digest.txt` file is created by running a tool on the contents of your
project.

## `getdown.txt`

The `getdown.txt` file contains everything that Getdown needs to know to deploy
and update your application. Here we'll show a very basic `getdown.txt` file,
and you can refer to [[Getdown Dot Text|this full description]] for info on all of
the configuration options.

Here is a basic `getdown.txt` file, which we'll explain in comments in the file:

    # The URL from which the client is downloaded
    appbase = http://myapplication.com/myapp/
    
    # UI Configuration
    ui.name = My Application
    
    # Application jar files
    code = application.jar
    
    # The main entry point for the application
    class = myapplication.MyApplication

The `appbase` is the URL from which your client will be downloaded. All of the
file names in `getdown.txt` are relative to this URL. For example, in the above
case, the following files would be downloaded:

- http://myapplication.com/myapp/getdown.txt
- http://myapplication.com/myapp/digest.txt
- http://myapplication.com/myapp/application.jar

## `digest.txt`

The `digest.txt` file is created by running
`com.threerings.getdown.tools.Digester` on your Getdown project directory.
First download `getdown-tools.jar` from the
[downloads page](http://code.google.com/p/getdown/downloads/list).

Now, supposing you have a directory structure that looks like so:

    myapp/getdown.txt
    myapp/application.jar

You can generate the `digest.txt` file like so:

    % java -classpath getdown-tools.jar com.threerings.getdown.tools.Digester myapp

Where `myapp` is the path to the `myapp` directory that contains your client
files. This will report:

    Generating digest file 'myapp/digest.txt'...

And you should then see a `digest.txt` file in your `myapp` directory along
with your client.

Instructions for generating the `digest.txt` as a part of your application
build can be found on the [[Build Integration|build integration]] page.

## Hosting

The `myapp` directory now contains everything you need to serve your
application via Getdown. You will need to put the contents of `myapp` on your
webserver so that it can be downloaded via the
`http://myapplication.com/myapp/` URL described in your `getdown.txt` file. The
webserver does not need to support any special features, it just needs to serve
the contents of the `myapp` directory via normal HTTP.

## Testing

To test that your application is working. You can do the following. First
download the `getdown-client.jar` client jar file from the
[downloads page](http://code.google.com/p/getdown/downloads/list). Now create a
"stub" installation directory that looks like the following:

    myapp/getdown-client.jar
    myapp/getdown.txt

But the contents of the `getdown.txt` file in your stub directory need contain
only a single line:

    appbase = http://myapplication.com/myapp/

Eventually, you will create per-platform installers that create this stub
directory and set up application launchers that are appropriate to the platform
in question (or you'll launch Getdown as an applet). But for now, we can run
Getdown manually from the command line.

With the above directory structure set up, run the following command:

    % java -jar myapp/getdown-client.jar myapp

This will run Getdown, and cause it to download and validate your application
and then execute it.

## Installers and Applet

Creating per-platform installers is unfortunately a more complex process than
can be described in this quick start. See the [[Installers|installers]] page for
detailed instructions.

If you wish to run Getdown as a signed applet, in lieu of or along side
downloadable installers, see the [[Applet Mode|applet mode]] page for detailed
instructions.

## Updating Your App

In order to update your app, you simply create a new staging directory for your
client with updated application jar files (and an updated `getdown.txt` if you
need to add additional data to your app), rerun the Digester to generate an
updated `digest.txt` and then upload the contents of `myapp` to your webserver,
overwriting the old `myapp` directory contents.

Getdown will check the last modified timestamp of the `getdown.txt` file on the
web server and if it is newer than the version the client has locally, it will
download any files that have changed. Files that have not changed (as
determined by their MD5 hash in `digest.txt`) will not be redownloaded.

Note that this is how Getdown behaves in __versionless__ mode. It is also
possible for applications to provide an explicit version number for each
application and control exactly when Getdown attempts to download a new version
of the application. For details on this mode of operation see [[Versioned Mode|the documentation on explicit-versioned mode]].

## More Details

See the main [[Documentation|documentation]] page for more details.