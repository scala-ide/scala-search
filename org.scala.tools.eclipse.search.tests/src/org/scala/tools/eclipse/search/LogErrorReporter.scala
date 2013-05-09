package org.scala.tools.eclipse.search

import scala.tools.eclipse.logging.HasLogger

trait LogErrorReporter extends ErrorReporter with HasLogger {

  def reportError(msg: String): Unit = logger.debug(msg)

}