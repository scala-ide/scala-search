package org.scala.tools.eclipse.search

import org.scalaide.core.ScalaPlugin

class ScalaSearchException(msg: String) extends Exception(msg + s" This is a bug, please file a ticket at ${ScalaPlugin.IssueTracker}.")