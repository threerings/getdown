# An overview of Getdown's design.

Getdown consists of a single jar file that contains the code for downloading
and validating files along with images and configuration provided by the
deploying application to brand the display shown to the user while downloading
and installing files. A JRE must also be deployed on the machine that uses
Getdown. Civilized operating systems like Mac OS X come with one already
installed, other OSes — for business (Microsoft), or philosophical (Linux)
reasons — will require that a JRE be installed along with Getdown.

# Control files

    getdown.txt
    digest.txt
    version.txt [only used by versioned application installations]
    [files associated with the application]

These files all reside in the application's deployment directory, which is
provided to Getdown as an invocation parameter (allow us to resort to the Unix
command line for an example):

    % java -jar getdown-client.jar /your/app/dir

Before invoking the application, Getdown will chdir to the application
deployment directory to simplify the life of simple applications. It is also
possible to provide the directory as an argument (or system property) to your
application when it is invoked. See below for details.

Note: all control files are in UTF-8 encoding.

The getdown.txt file looks like so:

    # This indicates the version of the application defined by this
    # Getdown deployment file. It must be a single integer. If this
    # is omitted, this is assumed to be a non-versioned application
    # deployment.
    version = 15
    
    # This defines the URL from which everything is downloaded. In a
    # versioned application deployment, this path must contain the version
    # number as %VERSION%.
    appbase = http://www.yoursite.com/path/to/your/app/v%VERSION%
    
    # The code resources are all specified relative to the appbase and are
    # installed with the same path, but relative to the application
    # deployment directory. These jar files are all placed in the classpath
    # when invoking your application. Your application's deployment
    # classfile may reside in any of these files, it need not be the first.
    code = application.jar
    code = happy/funcode.jar
    
    # The resources are all specified relative to the appbase and are
    # installed with the same path, but relative to the application
    # deployment directory.
    resource = media/kibbles.jar
    resource = media/bits.jar
    resource = media/gravy_bits.jar
    
    # These options are passed to the JVM before the specification of the
    # application on the command line. Herein one would specify things like
    # maximum heap size or -Xdisable_annoying_bugs, etc. Additionally, system
    # properties should be defined here. Note that %APPDIR% will be replaced
    # with the current application deployment directory.
    jvmarg = -Xmx128M
    jvmarg = -Dmonkeys=true
    jvmarg = -Dappdir=%APPDIR%
    
    # This defines the main class of your application. This should contain a
    # main() function which will be the entry point to your code.
    class = com.yoursite.crazy.Application
    
    # These arguments are passed to your application at invocation time. Note
    # that %APPDIR% will be replaced with the current application deployment
    # directory.
    apparg = peanuts
    apparg = %APPDIR%

The digest.txt file looks like so:

    getdown.txt = 06ddb6c55541fa9ef179a4c5029412df
    application.jar = 20261a3bd402f339b6a3cb213bb821c7
    happy/funcode.jar = 0326044be41f575d060dd43f76b8e888
    media/kibbles.jar = 0b2ea3d3e006b4fa42956d450849d1dd
    media/bits.jar = cb6560616a38c7520be7e85bbfd2355d
    media/gravy_bits.jar = d40d9ffec9d8309b149f692fe760c7bf
    meta = 943bd46399a77df39a02c33d428471c4

Each file in the deployment is listed along with its MD5 digest, and to ensure
the validity of the digest file itself, the meta property contains the MD5
digest of the concatenation of all files and digests contained in the digest
file.

The version.txt file contains a single integer (in ASCII text) which indicates
the latest version of the application. This version may not yet be installed
and in fact, this is how the application indicates to the Getdown system that a
new version of the application is available and should be installed. The
application must determine that a new version is available by its own means
because we can't trust HTTP to do it. Chances are, any application for which
version updates are critical (ie. massively multiplayer online games) has its
own mechanism of knowing whether or not it is using the appropriate version and
for those in which updates are not critical (ie. standalone applications), it
can just issue an HTTP request and write the result out to the version.txt
file.

# Normal operation

When Getdown is invoked, it takes the following steps:

1. Read in the contents of the getdown.txt, digest.txt and version.txt files.
1. Validate the MD5 digest of the digest.txt file. If it is corrupt, attempt to download a new copy and restart.
1. Validate the MD5 digest of the getdown.txt file. If it is corrupt, attempt to download a new copy and restart.
1. If the version specified in version.txt is greater than the version specified in getdown.txt proceed to the Upgrade path, otherwise, proceed with the Validation path.

### Validation path

1. For each code and resource file, there may or may not exist an associated validation marker file which has the same path as the original file with a v appended to its name. For example: application.jar would have an associated application.jarv file.
1. If the file itself does not exist, delete any associated validation file and download the file itself.
1. If the validation file exists, assume the file is validated and continue with the next file.
1. Compute the MD5 digest of the file and compare it with the value in the digest.txt file.
1. If they are the same, create the validation file and continue with the next file.
1. If they differ, delete the original file and return to step 2.
1. Once all files are validated, proceed to the Execution path.

### Upgrade path

1. Delete all validation files for the current installation.
1. Compute the appbase for one version higher than the currently installed version.
1. Download the getdown.txt and digest.txt files for that version to a temporary location.
1. Validate the MD5 digest of both files, if either fails, return to step 3.
1. Attempt to download a patch.dat file using the target version's appbase.
1. If such a file was located, apply the patch, otherwise proceed to the Last ditch upgrade path.
1. Replace the getdown.txt and digest.txt files with the target version files.
1. If we have reached the version.txt version, proceed to the Validation path otherwise return to step 2 and install the next version.

### Last ditch upgrade path

1. Compute the appbase for the version.txt version.
1. Download the getdown.txt and digest.txt files for the target version.
1. Validate the MD5 digest of both files, if either fails, return to step 2.
1. Replace the current getdown.txt and digest.txt files with the target version files.
1. Proceed to the Validation path. Any currently installed files that have changed from the installed version to the desired version will fail their digest check and be deleted and redownloaded.

### Execution path

1. Construct the command line based on the information in getdown.txt.
1. Execute the command line in a separate process.
1. If the command immediately fails: assume one or more of the jar files are corrupt, delete all validation marker files and restart.
1. Exit the Getdown bootstrap process.

# Failure scenarios and their recovery

One of the strengths of Getdown is that it is designed with failure in mind, a
sentiment I can't say was foremost on the mind of the designers of Java Web
Start. As such, we assume that at any point, network connections can go away,
gamma rays can twiddle bits on a user's harddrive, monkeys can break into a
user's house in the middle of the night and fire up the hex editor. Everything
outside our servers is a jungle and we treat it as such. Probable failure
scenarios and their recovery are thus outlined below:

### A file is partially downloaded due to network failure

Whenever a file is downloaded from the network, it is immediately subject to an
MD5 digest check. If that check fails, we delete the original and download the
file anew.

A code or resource file becomes corrupt after previously being successfully
installed If the application discovers an integrity problem, it can simply
delete the validation marker file which will cause Getdown to validate and
redownload the file on the next invocation. Alternatively, we may wish to
implement a system whereby periodically files are revalidated based on an
interval defined in the getdown.txt file. During application startup, we stat()
the validation files, so we can easily compare the last modified time of those
files to the current time and force a revalidation of a file that hasn't been
validated in sufficiently long.

### The digest.txt file becomes corrupt

It contains an internal integrity check, so we will immediately discover the
error and simply redownload the file.

### The getdown.txt file becomes corrupt

As we must obtain our appbase information from the getdown.txt file, we go
ahead and use whatever appbase we can find in the current getdown.txt file and
attempt to redownload getdown.txt. If that file became corrupt in just the
right way, it could corrupt the appbase and we would be hosed. However, in that
one case, the user could rerun the installer, which would overwrite the
getdown.txt file with a fresh copy and we could recover from there. We could
require that the URL be passed as an argument to the getdown-client.jar JVM
invocation, but the file that passes that argument could also become corrupt,
so it doesn't buy us any robustness. Obviously, we can't automatically recover
if the user formats their harddrive either. Every system has some limitations,
we simply work to minimize them.

### The version.txt file becomes corrupt

In the event that the version.txt file does not contain a valid integer, we
simply ignore it and assume the current version is the desired version and run
the application. It is then responsible for recreating a valid version.txt file
if it knows that the current version is out of date. The application must be
vigilant about reporting errors in creating the version.txt file because one
can imagine a scenario where the user's harddrive is full and the application
fails to create the version.txt file and reruns Getdown with the expectation
that it can do something about it, when in reality the application needs to
tell the user to free up some harddrive space so that it can create the file
properly.

### The user's drive is full or their network connection is down

If at any point we attempt a process, fail, and retry more than three times, we
report to the user the most sensible information we can offer based on our
knowledge of the failure (network timeout, failure to write to a file, etc.)
and instruct them to either try again later or do something to remedy the
problem (run scandisk, free up some harddrive space, etc.). The user should
never have to do anything other than simply rerun the application after taking
some remedial action and it should cleanly pick up where it left off with the
upgrade and installation process.

# Application deployment and patch creation

An application deployment will be contained within a single directory (and its
subdirectories) on the distribution server just as it is contained thusly on a
user's machine. Each version of an application is stored in a separate
directory on the distribution server. This simplifies the process of deployment
which is as follows:

1. Install all application files into the appropriate directory on the deployment server.
1. Create (or copy from a previous version and modify) the getdown.txt file for the version in question. Place it with the application files in the appropriate directory.
1. Run a provided tool to generate the digest.txt file. This tool can also be informed of the deployment directory for the previous version of the application and it will generate a patch.dat file that the Getdown client can use to upgrade from the previous version to this version. The format of that file and the exact patch mechanism is not defined here.
1. If the application is deployed across a set of replicated servers, all files in the application directory should be mirrored to those servers.

After these simple steps have been performed, the update is ready to go.
Whatever application specific trigger that will instruct the client to download
the new version can be enacted.