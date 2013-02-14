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
   * Indexes all souces in the current workspace.
   */
  def indexWorkspace(root: IWorkspaceRoot) = {
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
   * Indexes all sources in `project`
   */
  def indexProject(proj: ScalaProject) = {
    timed("Indexing project %s".format(proj)) {
      proj.allSourceFiles.foreach { indexIFile }
    }
  }

  /**
   * Indexes the parse tree of an IFile if the IFile is pointing
   * to a Scala source file. 
   */
  def indexIFile(file: IFile): Unit = {
    val path = file.getFullPath().toOSString()
    ScalaSourceFile.createFromPath(path).foreach { cu =>
        indexScalaFile(cu)
    }
  }

  def indexScalaFile(sf: ScalaSourceFile): Unit = {
    OccurrenceCollector.findOccurrences(sf).fold(
      fail => logger.debug(fail),
      occurrences => index.addOccurrences(occurrences))
  }

}