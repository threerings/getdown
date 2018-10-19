# Getdown Releases

## 1.8.0 - Oct 19, 2018

* Added support for manually specifying the thread pool size via `-Dthread_pool_size`. Also reduced
  the default thread pool size to `num_cpus-1` from `num_cpus`.

* Added support for bundling a `bootstrap.properties` file with the Getdown jar file, which can
  specify defaults for `appdir`, `appbase` and `appid`.

* Added support for a host URL whitelist. Getdown can be custom built to refuse to operate with any
  URL that does not match the built-time-specified whitelist. See `core/pom.xml` for details.

* Removed the obsolete support for running Getdown in a signed applet. Applets are no longer
  supported by any widely used browser.

* Split the project into multiple Maven modules. See the notes on [migrating from 1.7 to 1.8] for
  details.

* A wide variety of small cleanups resulting from a security review generously performed by a
  prospective user. This includes various uses of deterministic locales and encodings instead of
  the platform default locale/encoding, in cases where platform/locale-specific behavior is not
  desired or needed.

* Made use of `appid` fall back to main app class if no `appid`-specific class is specified.

* Added support for marking resources as executable (via `xresource`).

* Fixed issue where entire tracking URL was being URL encoded.

* Changed translations to avoid the use of the term 'game'. Use 'app' instead.

## 1.7.1 - Jun 6, 2018

* Made it possible to use `appbase_domain` with `https` URLs.

* Fixed issue with undecorated splash window being unclosable if failures happen early in
  initialization process. (#57)

* Added support for transparent splash window. (#92)

* Fixed problem with unpacked code resources (`ucode`) and `pack.gz` files. (#95)

* Changed default Java version regex to support new Java 9+ version formats. (#93)

* Ensure correct signature algorithm is used for each version of digest files. (#91)

* Use more robust delete in all cases where Getdown needs to delete files. This should fix issues
  with lingering files on Windows (where sometimes delete fails spuriously).

## 1.7.0 - Dec 12, 2017

* Fixed issue with `Digester` thread pool not being shutdown. (#89)

* Fixed resource unpacking, which was broken by earlier change introducing resource installation
  (downloading to `_new` files and then renaming into place). (#88)

* The connect and read timeouts specified by system properties are now used for all the various
  connections made by Getdown.

* Proxy detection now uses a 5 second connect/read timeout, to avoid stalling for a long time in
  certain problematic network conditions.

* Getdown is now built against JDK 1.7 and requires JDK 1.7 (or newer) to run. Use the latest
  Getdown 1.6.x release if you need to support Java 1.6.

## 1.6.4 - Sep 17, 2017

* `digest.txt` (and `digest2.txt`) computation now uses parallel jobs. Each resource to be verified
  is a single job and the jobs are doled out to a thread pool with #CPUs threads. This allows large
  builds to proceed faster as most dev machines have more than one core.

* Resource verification is now performed in parallel (similar to the `digest.txt` computation, each
  resource is a job farmed out to a thread pool). For large installations on multi-core machines,
  this speeds up the verification phase of an installation or update.

* Socket reads now have a 30 second default timeout. This can be changed by passing
  `-Dread_timeout=N` (where N is seconds) to the JVM running Getdown.

* Fixed issue with failing to install a downloaded and validated `_new` file.

* Added support for "strict comments". In this mode, Getdown only treats `#` as starting a comment
  if it appears in column zero. This allows `#` to occur on the right hand side of configuration
  values (like in file names). To enable, put `strict_comments = true` in your `getdown.txt` file.

## 1.6.3 - Apr 23, 2017

* Fixed error parsing `cache_retention_days`. (#82)

* Fixed error with new code cache. (9e23a426)

## 1.6.2 - Feb 12, 2017

* Fixed issue with installing local JVM, caused by new resource installation process. (#78)

* Local JVM now uses absolute path to avoid issues with cwd.

* Added `override_appbase` system property. This enables a Getdown app that normally talks to some
  download server to be installed in such a way that it instead talks to some other download
  server.

## 1.6.1 - Feb 12, 2017

* Fix issues with URL path encoding when downloading resources. (84af080b0)

* Parsing `digest.txt` changed to allow `=` to appear in the filename. In `getdown.txt` we split on
  the first `=` because `=` never appears in a key but may appear in a value. But in `digest.txt`
  the format is `filename = hash` and `=` never appears in the hash but may appear in the filename,
  so there we want to split on the _last_ `=` not the first.

* Fixed bug with progress tracking and reporting. (256e0933)

* Fix executable permissions on `jspawnhelper`. (#74)

## 1.6 - Nov 5, 2016

* This release and all those before it are considered ancient history. Check the commit history for
  more details on what was in each of these releases.

## 1.0 - Sep 21, 2010

* The first Maven release of Getdown.

## 0.1 - July 19, 2004

* The first production use of Getdown (on https://www.puzzlepirates.com which is miraculously still
  operational as of 2018 when this changelog was created).

[migrating from 1.7 to 1.8]: https://github.com/threerings/getdown/wiki/Migrate17to18
