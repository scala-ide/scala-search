package org.scala.tools.eclipse.search

import java.io.File
import scala.tools.eclipse.logging.HasLogger
import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.ui.plugin.AbstractUIPlugin
import org.osgi.framework.BundleContext
import org.scala.tools.eclipse.search.indexing.Index
import org.scala.tools.eclipse.search.indexing.SourceIndexer
import org.scala.tools.eclipse.search.jobs.ProjectIndexJob
import scala.collection.concurrent.TrieMap
import scala.collection.concurrent.Map
import org.eclipse.core.resources.IProject
import scala.tools.eclipse.ScalaProject
import scala.tools.eclipse.ScalaPlugin

object SearchPlugin extends HasLogger {

  final val PLUGIN_ID  = "org.scala.tools.eclipse.search"

  @volatile var plugin: SearchPlugin = _

  /**
   * Checks if the `file` is a type we know how to index.
   */
  def isIndexable(file: IFile): Boolean = {
    // TODO: https://scala-ide-portfolio.assembla.com/spaces/scala-ide/tickets/1001602
    Option(file).map( _.getFileExtension() == "scala").getOrElse(false)
  }
}

class SearchPlugin extends AbstractUIPlugin with HasLogger {

  private final val INDEX_DIR_NAME = "lucene-indices"

  @volatile private var indexLocation: File = _
  @volatile private var index: Index = _
  @volatile private var sourceIndexer: SourceIndexer = _

  override def start(context: BundleContext) {
    SearchPlugin.plugin = this
    super.start(context)

    indexLocation = getStateLocation().append(INDEX_DIR_NAME).toFile()
    index = new Index(indexLocation)
    sourceIndexer = new SourceIndexer(index)

    val root = ResourcesPlugin.getWorkspace().getRoot()
    root.getProjects().map(Option.apply).flatten.foreach { proj =>
      ScalaPlugin.plugin.asScalaProject(proj).foreach { sp =>
        ProjectIndexJob(sourceIndexer, sp)
      }
    }
  }

  override def stop(context: BundleContext) {
    super.stop(context)
    SearchPlugin.plugin = null
    indexLocation = null
    index = null
    sourceIndexer = null
  }

  def indexLocationForProject(project: IProject) = {
    new File(indexLocation.getAbsolutePath() + File.separator + project.getName())
  }

}