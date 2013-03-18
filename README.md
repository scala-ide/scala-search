Next Scala Search
=================

The next Scala Search Engine for Scala IDE.

Please, use the [scala-ide-user](http://groups.google.com/group/scala-ide-user/) Mailing List for questions and
comments. Or, if you are interested in contributing, drop us a email on [scala-ide-dev](http://groups.google.com/group/scala-ide-dev/).

Report A Bug
------------

File tickets in our [issue tracker](http://scala-ide-portfolio.assembla.com/spaces/scala-ide/support/tickets), or drop a message in the [scala-ide-user mailing list](https://groups.google.com/group/scala-ide-user) if you have any doubt.

Install it
----------

For the moment only nightlies are available.

### Requirements ###

* [Install Eclipse 3.7 (Indigo)](http://www.eclipse.org/downloads/packages/release/indigo/sr2).
* [Install the Scala IDE nightly for Scala 2.10.x](http://scala-ide.org/download/nightly.html#for_scala_210x).

### Update site ###

http://scala-ide.dreamhosters.com/nightly-update-scala-search-scalaide-master-210/site/

Building
--------

The build is configured using maven so you build by invoking the following:

    mvn clean package

Running it
----------

The easiest way to work on the plugin is to import the projects into Eclipse and run it using
the [Equinox Weaving Launcher](https://github.com/milessabin/equinox-weaving-launcher) plugin.
To install the Equinox Weaving Launcher, use the following Eclipse update site:

[http://www.chuusai.com/eclipse/equinox-weaving-launcher/](http://www.chuusai.com/eclipse/equinox-weaving-launcher/)

This adds the run configuration `Eclipse Application with Equinox Weaving`.

Links
-----

- [Jenkins Job](https://jenkins.scala-ide.org:8496/jenkins/view/Plugins%20%28Scala%20IDE%29/job/scala-search-nightly-master-2.10/?)
