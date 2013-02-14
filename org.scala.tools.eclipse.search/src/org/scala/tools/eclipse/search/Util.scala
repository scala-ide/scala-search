package org.scala.tools.eclipse.search

import scala.tools.eclipse.logging.HasLogger

/**
 * Object that contains various utility methods
 */
object Util extends HasLogger {

  /**
   * Will evaluate f and log the message `msg` togerther with the
   * number of seconds it took to evaluate `f`. It returns the value
   * computed by `f`
   */
  def timed[A](msg: String)(f: => A) = {
    val now = System.currentTimeMillis()
    f
    val elapsed = ((System.currentTimeMillis() - now) * 0.001)
    logger.debug("%s took %s seconds".format(msg, elapsed.toString))
  }

}