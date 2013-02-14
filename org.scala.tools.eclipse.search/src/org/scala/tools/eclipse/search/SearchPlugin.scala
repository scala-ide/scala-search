package org.scala.tools.eclipse.search

import java.io.File

import scala.tools.eclipse.logging.HasLogger

import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.ui.IStartup
import org.eclipse.ui.plugin.AbstractUIPlugin
import org.osgi.framework.BundleContext
import org.scala.tools.eclipse.search.indexing.LuceneIndex
import org.scala.tools.eclipse.search.indexing.SourceIndexer
import org.scala.tools.eclipse.search.jobs.IndexWorkspaceJob

class SearchPlugin extends AbstractUIPlugin with IStartup with HasLogger {

  import SearchPlugin._

  instance = Some(this)

  def earlyStartup(): Unit = {
    logger.debug("Ran early setup")
  }

  override def start(context: BundleContext) {
    logger.debug("Starting SearchPlugin")

    if (shouldIndexWorkspace) {
      initialWorkspaceJob.schedule()
      initialWorkspaceJob.setPriority(Job.LONG) // long running job
    } else logger.debug("-- Index already exists.")
  }

  override def stop(context: BundleContext) {
    logger.debug("Stopping the plugin")
    initialWorkspaceJob.cancel()
  }

}

object SearchPlugin {

  final val PLUGIN_ID  = "org.scala.tools.eclipse.search"
  private final val INDEX_DIR_NAME = "lucene-index"

  // Unfortunately, we need this.
  var instance: Option[SearchPlugin] = None

  private final val index = new LuceneIndex(indexLocation.get)
  private final val sourceIndexer = new SourceIndexer(index)

  final val initialWorkspaceJob = new IndexWorkspaceJob(sourceIndexer, root)

  def root = ResourcesPlugin.getWorkspace().getRoot()

  private def indexLocation: Option[File] = instance.map { in =>
    val stateLocation = in.getStateLocation()
    val path = stateLocation.append(INDEX_DIR_NAME)
    path.toFile()
  }

  /**
   * Whether or not we need to index the entire workspace.
   */
  def shouldIndexWorkspace: Boolean = {
    /* TODO: At some point this should be a bit more clever. Currently we
     *       assume that no other application will change the source code
     *       and as such we should only index the workspace if it hasn't 
     *       been indexed already
     */
    indexLocation.map { _.exists() } getOrElse false
  }

}