package org.scala.tools.eclipse.search

import java.io.File

trait TestUtil {

  def mkPath(xs: String*): String = {
    xs.mkString(File.separator)
  }
}