package org.scala.tools.eclipse.search

import org.scalaide.logging.HasLogger

import org.eclipse.ui.IStartup

/**
 * This class forces the plugin to be started at eclipse startup, i.e. it will create
 * an instance of SearchPlugin and invoke start(context) on it. If this class was not
 * here the plugin would be initialized lazily and we wouldn't be able to start
 * indexing and observing changes before some of the classes in this plugin were used.
 */
class Startup extends IStartup with HasLogger {

  override def earlyStartup() {}

}