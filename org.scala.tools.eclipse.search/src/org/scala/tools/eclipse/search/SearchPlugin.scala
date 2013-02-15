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
import org.eclipse.core.resources.IFile
import org.scala.tools.eclipse.search.jobs.UpdateIndexJob
import org.scala.tools.eclipse.search.jobs.UpdateIndexJob

/**
 * The main entry point for the plugin. This is instaintiated and started
 * by Eclipse
 */
class SearchPlugin extends AbstractUIPlugin with HasLogger {

  override def start(context: BundleContext) {
    logger.debug("Starting SearchPlugin")
    super.start(context)
    val stateLocation = getStateLocation()
    val path = stateLocation.append(SearchPlugin.INDEX_DIR_NAME)
    SearchPlugin.setup(path.toFile())
  }

  override def stop(context: BundleContext) {
    logger.debug("Stopping the plugin")
    SearchPlugin.stop()
    super.stop(context)
  }

}

object SearchPlugin extends HasLogger {

  val PLUGIN_ID  = "org.scala.tools.eclipse.search"

  private var indexLoc: File = _
  private var initialWorkspaceJob: IndexWorkspaceJob = _
  private var updateIndexJob: UpdateIndexJob = _

  private val INDEX_DIR_NAME = "lucene-index"

  /**
   * Initializes the index and jobs. This can't be done upon initilization
   * because we need an instance of SearchPlugin to get the proper location
   * of the index.
   */
  private def setup(indexLocation: File): Unit = {
    val index = new LuceneIndex(indexLocation)
    val sourceIndexer = new SourceIndexer(index)
    indexLoc = indexLocation
    initialWorkspaceJob = new IndexWorkspaceJob(sourceIndexer, root)
    updateIndexJob = new UpdateIndexJob(sourceIndexer, interval = 5000)

    // If needed, start the initial index of the entire workspace.
    if (shouldIndexWorkspace) {
      logger.debug("Started Indexing")
      initialWorkspaceJob.schedule()
      initialWorkspaceJob.setPriority(Job.LONG) // long running job
    } else logger.debug("Index already exists.")

    // Background job that periodically updates the index.
    updateIndexJob.setSystem(true)
    updateIndexJob.schedule(5000) // TODO: Would be nicer to start this job when the indexing is done.
  }

  private def stop(): Unit = {
    initialWorkspaceJob.cancel()
    updateIndexJob.cancel()
  }

  def indexLocation: Option[File] = Option(indexLoc)

  /**
   * The root of the current workspace.
   */
  def root = ResourcesPlugin.getWorkspace().getRoot()

  /**
   * Checks if the initial indexing job is still running.
   */
  def isInitialIndexRunning: Boolean = {
    (initialWorkspaceJob.getState() == org.eclipse.core.runtime.jobs.Job.WAITING ||
     initialWorkspaceJob.getState() == org.eclipse.core.runtime.jobs.Job.RUNNING)
  }

  /**
   * Checks if the `file` is a type we know how to index.
   */
  def isIndexable(file: IFile): Boolean = {
    // TODO: At some point we want to make the acceptable files extensible.
    // such that frameworks such as play can have their template files indexed.
    file.getFileExtension() == "scala"
  }

  /**
   * Whether or not we need to index the entire workspace.
   */
  private def shouldIndexWorkspace: Boolean = {
    /* TODO: At some point this should be a bit more clever. Currently we
     *       assume that no other application will change the source code
     *       and as such we should only index the workspace if it hasn't
     *       been indexed already
     */
    indexLocation.map { !_.exists() } getOrElse false
  }
}