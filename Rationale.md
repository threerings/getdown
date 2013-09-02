# Why we decided to replace Java Web Start.

# Major design goals

Java Web Start, how dost thee spite me? Let me count the ways. To illustrate
why we should take the pains to implement such a system instead of using Java
Web Start, let us state its primary design goals:

- To ensure the integrity and operability of the installed application at all costs and with zero effort required from the user. Every file will be checksummed and any checksum failure will result in automatic redownload of the corrupt file. Periodically or at the request of the deployed application, the installed files will be revalidated to ensure that they have not become corrupt while installed on the user's machine.

  *I have seen Java Web Start fail unrecoverably in more ways than I have
  fingers to count them, frequently with the only recourse to the user to be
  totally uninstalling and reinstalling my application and/or Java.*

- To navigate the labrynthine mess that is the Internet in as robust a manner as possible. This means doing things in such a way as to avoid problems with caching proxies, web browser caches and any other sinister entity that insinuates itself between us and our user. Getdown will use files that never change. For versioned application deployment (used in production systems), once a file is deployed at a certain path, its contents do not change (to understand how Getdown knows when to download a new version see the [[Design]]).

  *Java Web Start frequently fails thanks to over agressive caching proxies,
  proxies that mangle response headers, proxies that do strange things to the
  request URI. 'But it's the proxies that are at fault!' you say. Tell that to
  the user whose application no longer works.*

- To scale to thousands or tens of thousands of users updating their application simultaneously. When we deploy an update to our massively-multiplayer game, everyone has to download it at the same time. There's no way around it. Getdown will use nothing but plain files which can be served using the simplest, most scalable HTTP server one can get their hands on.

  *Java Web Start made questionable design choices in this regard by making use
  of a sophisticated protocol that requires a running servlet engine to service
  (for those who don't want to reimplement the complex protocol using a more
  lightweight mechanism). Hardly scalable and annoyingly fragile.*

- To do nothing other than what is necessary to download, update and invoke a single application.

  *Java Web Start provides a plethora of marginally useful functionality in the
  form of an application manager, web browser integration, a security model,
  etc. etc. etc. While those things are lovely for Sun and maybe even useful to
  Bigcorp, we care only about getting our application to our users in as simple
  an robust a manner as possible, not the creation of a general purpose
  platform for application deployment and management.*

# Minor design goals

If not for the major design goals, we wouldn't be doing this. But since we are,
let's take the opportunity to remedy some of the various annoyances presented
by Java Web Start.

- Getdown will store the entire application in a single directory (and its subdirectories) and that directory may reside anywhere on the file system. That directory will, by design, be made known to the application itself.

  *Java Web Start annoyingly insists that data be placed across a vast swathe
  of directories, the locations of which are not knowable by the application
  without making unsupported, prone to future breakage, guesses. Moreover,
  without the user making use of the 'Advanced' tab of the Java Web Start
  management application, that directory cannot reside anywhere but the user's
  home directory; nor can different applications be made to reside in different
  places on the file system.*

- Getdown will allow any arguments to be passed to the underlying JVM, not just a particular set that have been declared acceptable by the application deployment system.

- Getdown will work robustly with a private JVM installation.

  *Java Web Start claims to support this option, but heaven forbid you actually
  use it and then try something like specifying a non-default heap size which
  will cause it to choke and die because it can't find the JVM to reinvoke with
  the new heap size when running your application.*

- Once your application is running, Getdown will not open windows and cause unavoidable deadlocks if you happen to run your application in full-screen mode. So-called desktop integration will be the responsibility of the application. Any application that cares two bits for its users will provide an installer and that installer will probably not be cross platform and will certainly be in a much better position to "integrate" your application with the desktop.