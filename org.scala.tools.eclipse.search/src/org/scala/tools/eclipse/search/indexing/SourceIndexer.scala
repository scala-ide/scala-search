package org.scala.tools.eclipse.search.indexing

import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.logging.HasLogger
import org.eclipse.core.resources.IWorkspaceRoot
import scala.tools.eclipse.ScalaPlugin
import scala.tools.eclipse.ScalaProject
import org.scala.tools.eclipse.search.Util._
import org.eclipse.core.resources.IFile
import scala.tools.eclipse.util.Utils

/**
 * Indexes Scala sources and add all occurrences to the `index`.
 */
class SourceIndexer(index: Index) extends HasLogger {

  /**
   * Indexes all sources in `project`. This removes all previous occurrences recorded in
   * that project.
   */
  def indexProject(proj: ScalaProject): Unit = {
    Utils.debugTimed("Indexing project %s".format(proj)) {
      proj.allSourceFiles.foreach { indexIFile }
    }
  }

  /**
   * Indexes the parse tree of an IFile if the IFile is pointing to a Scala source file.
   * This removes all previous occurrences recorded in that file.
   */
  def indexIFile(file: IFile): Unit = {
    scalaSourceFileFromIFile(file).foreach { cu => indexScalaFile(cu) }
  }

  /**
   * Indexes the occurrences in a Scala file. This removes all previous occurrences
   * recorded in that file.
   */
  def indexScalaFile(sf: ScalaSourceFile): Unit = {
    logger.debug(s"Indexing document: ${sf.file.path}")
    index.removeOccurrencesFromFile(sf.file.file, sf.getUnderlyingResource().getProject())
    OccurrenceCollector.findOccurrences(sf).fold(
      fail => logger.debug(fail),
      occurrences => index.addOccurrences(occurrences, sf.getUnderlyingResource().getProject()))
  }

}