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

  @volatile private var pluginInstance: SearchPlugin = _

  def plugin: Option[SearchPlugin] = Option(pluginInstance)

  /**
   * Checks if the `file` exists and is a type we know how to index.
   */
  def isIndexable(file: IFile): Boolean = {
    // TODO: https://scala-ide-portfolio.assembla.com/spaces/scala-ide/tickets/1001616
    Option(file).filter(_.exists).map( _.getFileExtension() == "scala").getOrElse(false)
  }
}

class SearchPlugin extends AbstractUIPlugin with HasLogger {

  private final val INDEX_DIR_NAME = "lucene-indices"

  @volatile private var indexLocation: File = _
  @volatile private var index: Index = _
  @volatile private var sourceIndexer: SourceIndexer = _
  @volatile private var jobs: Seq[Job] = _

  override def start(context: BundleContext) {
    SearchPlugin.pluginInstance = this
    super.start(context)

    indexLocation = getStateLocation().append(INDEX_DIR_NAME).toFile()
    index = new Index(indexLocation)
    sourceIndexer = new SourceIndexer(index)

    val root = ResourcesPlugin.getWorkspace().getRoot()

    jobs = for {
      project <- root.getProjects().map(p => Option.apply(p).flatMap(ScalaPlugin.plugin.asScalaProject)).flatten
    } yield {
      val p = ProjectIndexJob(sourceIndexer, index, project)
      p.schedule()
      p
    }
  }

  override def stop(context: BundleContext) {
    super.stop(context)
    jobs.foreach( _.cancel() )
    SearchPlugin.pluginInstance = null
    indexLocation = null
    index = null
    sourceIndexer = null
  }

  def indexLocationForProject(project: IProject) = {
    new File(indexLocation.getAbsolutePath() + File.separator + project.getName())
  }

}