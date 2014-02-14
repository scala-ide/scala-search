package org.scala.tools.eclipse.search

import org.scalaide.logging.HasLogger

class LogErrorReporter extends ErrorReporter with HasLogger {
  def reportError(msg: String): Unit = logger.debug(msg)
}