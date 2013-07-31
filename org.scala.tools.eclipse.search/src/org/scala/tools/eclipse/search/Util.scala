package org.scala.tools.eclipse.search

import scala.tools.eclipse.InteractiveCompilationUnit
import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.logging.HasLogger

import org.eclipse.core.resources.IFile

/**
 * Object that contains various utility methods
 */
object Util extends HasLogger {
  def scalaSourceFileFromIFile(file: IFile): Option[ScalaSourceFile] = {
    val path = file.getFullPath().toOSString()
    ScalaSourceFile.createFromPath(path)
  }

  def getWorkspaceFile(cu: InteractiveCompilationUnit): Option[IFile] = {
    // Accessing hit.cu.workspaceFile will throw an
    // exception if hit.cu doesn't exist or the
    // project is closed so we have to check that first.
    if (cu.exists && cu.scalaProject.underlying.isOpen) Option(cu.workspaceFile) else None
  }
}