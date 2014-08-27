package org.scala.tools.eclipse.search

import org.scalaide.core.SdtConstants

class ScalaSearchException(msg: String) extends Exception(msg + s" This is a bug, please file a ticket at ${SdtConstants.IssueTracker}.")