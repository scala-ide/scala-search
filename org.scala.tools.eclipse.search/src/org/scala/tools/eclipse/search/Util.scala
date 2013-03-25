package org.scala.tools.eclipse.search

import scala.tools.eclipse.logging.HasLogger
import scala.tools.eclipse.javaelements.ScalaSourceFile
import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.runtime.Path

/**
 * Object that contains various utility methods
 */
object Util extends HasLogger {

  def scalaSourceFileFromIFile(file: IFile): Option[ScalaSourceFile] = {
    val path = file.getFullPath().toOSString()
    ScalaSourceFile.createFromPath(path)
  }

}