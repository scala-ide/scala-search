Next Scala Search
=================

The next Scala Search Engine for Scala IDE. Currently there's not much
to see here.

Building
--------

The build is configured using maven so you build by invoking the following:

    mvn -P scala-ide-master-scala-trunk clean package
    
Running it
----------

The easiest way to work on the plugin is to import the projects into Eclipse and run it using 
the [Equinox Weaving Launcher](https://github.com/milessabin/equinox-weaving-launcher) plugin.
To install the Equinox Weaving Launcher, use the following Eclipse update site:

[http://www.chuusai.com/eclipse/equinox-weaving-launcher/](http://www.chuusai.com/eclipse/equinox-weaving-launcher/)

This adds the run configuration `Eclipse Application with Equinox Weaving`.

Links
-----

- [Jenkins Job](https://jenkins.scala-ide.org:8496/jenkins/job/scala-search-nightly-2.1-2.10/)