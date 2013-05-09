package org.scala.tools.eclipse

package object search {
  /**
   * Implicit value class that makes it easier to log or report errors
   * inside for-comprehensions.
   */
  implicit class ErrorHandlingOption[A](val op: Option[A]) extends AnyVal {
    def onEmpty(f: => Unit): Option[A] = {
      if (op.isEmpty) {
        f
        None
      } else op
    }
  }
}