package org.scala.tools.eclipse.search

import scala.util.control.Exception._

object using {
  def apply[T <: java.io.Closeable, R](resource: => T, handlers: Catch[R] = noCatch)(body: T => R): R = {
    handlers {
      val r = resource
      handlers.andFinally(ignoring(classOf[Any]){ r.close() }).apply{
        body(r)
      }
    }
  }
}