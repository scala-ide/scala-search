package org.scala.tools.eclipse.search.indexing

import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.logging.HasLogger
import org.eclipse.core.resources.IWorkspaceRoot
import scala.tools.eclipse.ScalaPlugin
import scala.tools.eclipse.ScalaProject
import org.scala.tools.eclipse.search.Util._
import org.eclipse.core.resources.IFile

/**
 * Indexes Scala sources and add all occurrences to the `index`.
 */
class SourceIndexer(index: LuceneIndex) extends HasLogger {

  /**
   * Indexes all sources in the current workspace. This removes all previous occurrences
   * recorded in that workspace.
   */
  def indexWorkspace(root: IWorkspaceRoot): Unit = {
    root.getProjects().foreach { p =>
      if (p.isOpen()) {
        ScalaPlugin.plugin.asScalaProject(p).map( proj => {
          indexProject(proj)
        }).getOrElse(logger.debug("Couldn't convert to scala project %s".format(p)))
      } else {
        logger.debug("Skipping %s because it is closed".format(p))
      }
    }
  }

  /**
   * Indexes all sources in `project`. This removes all previous occurrences recorded in
   * that project.
   */
  def indexProject(proj: ScalaProject): Unit = {
    timed("Indexing project %s".format(proj)) {
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
    index.removeOccurrencesFromFile(sf.file.file)
    OccurrenceCollector.findOccurrences(sf).fold(
      fail => logger.debug(fail),
      occurrences => index.addOccurrences(occurrences))
  }

}