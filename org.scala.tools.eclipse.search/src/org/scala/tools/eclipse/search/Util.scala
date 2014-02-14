package org.scala.tools.eclipse.search

import org.scalaide.core.compiler.InteractiveCompilationUnit
import org.scalaide.core.internal.jdt.model.ScalaSourceFile
import org.scalaide.logging.HasLogger
import org.eclipse.core.resources.IFile
import scala.reflect.io.AbstractFile
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.runtime.Path

/**
 * Object that contains various utility methods
 */
object Util extends HasLogger {

  def scalaSourceFileFromAbstractFile(file: AbstractFile): Option[ScalaSourceFile] = {

    for {
      abstractFile <- Option(file) onEmpty
                      logger.debug("the AbstractFile was null")
      normalFile   <- Option(abstractFile.file) onEmpty
                      logger.debug("No scala file was attatched to the symbol")
      workspace     = ResourcesPlugin.getWorkspace()
      location      = Path.fromOSString(normalFile.getAbsolutePath())
      ifile         = workspace.getRoot().getFileForLocation(location)
      scalaFile    <- scalaSourceFileFromIFile(ifile) onEmpty
                      logger.debug("Couldn't get ScalaSourceFile from File " + normalFile.getAbsolutePath)
    } yield scalaFile
  }

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