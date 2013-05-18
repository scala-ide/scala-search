package org.scala.tools.eclipse.search


/**
 * Convenience trait for reporting errors to the user.
 */
trait ErrorReporter {

  def reportError(msg: String): Unit

}