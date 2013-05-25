package org.scala.tools.eclipse.search

import scala.tools.eclipse.logging.HasLogger

class LogErrorReporter extends ErrorReporter with HasLogger {
  def reportError(msg: String): Unit = logger.debug(msg)
}