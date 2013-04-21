package org.scala.tools.eclipse.search

import scala.collection.mutable
import scala.tools.eclipse.logging.HasLogger
import scala.tools.eclipse.ScalaPlugin

import org.eclipse.core.resources.IProject
import org.scala.tools.eclipse.search.indexing.Index

import org.scala.tools.eclipse.search.indexing.SourceIndexer
import org.scala.tools.eclipse.search.jobs.ProjectIndexJob

/**
 * Responsible for keeping track of the various indexing jobs. It uses
 * ProjectChangeObserver to keep track of Resource Events related to
 * projects.
 * 
 * @note This trait is thread-safe.
 */
class IndexJobManager(index: Index with SourceIndexer) extends Lifecycle with HasLogger {

  private val lock = new Object

  /** Has the manager been started?
   *  @note Guarded by `lock`.
   */
  private var active: Boolean = false

  /** Listener for reacting to project's open/closed/deleted events.
   *  @note Guarded by `lock`.
   */
  private var observer: Observing = _

  /** Keeps track of indexing job for each Scala project.
   * @note Guarded by `lock`.
   */
  private val indexingJobs: mutable.Map[IProject, ProjectIndexJob] = 
    new mutable.HashMap[IProject, ProjectIndexJob]

  override def startup() = lock.synchronized {
    if(active) 
      throw new ScalaSearchException("Index manager was already started.")

    // Important to do this '''before''' registering the listener. 
    active = true

    /* Mind that the listener is initialized here (and not in the constructor) to prevent the 
     * `this` reference to escape before it is properly constructed. Failing to do so can lead 
     * to concurrency hazards.
     */
    observer = ProjectChangeObserver(
      onOpen = startIndexing(_),
      onNewScalaProject = startIndexing(_),
      onClose = stopIndexing(_),
      onDelete = project => {
        stopIndexing(project)
        if(index.indexExists(project))
          index.deleteIndex(project)
      })
  }

  override def shutdown() = lock.synchronized {
    ensureActive()
    observer.stop()
    observer = null
    indexingJobs.keys.foreach(stopIndexing)
    active = false
  }

  def startIndexing(project: IProject): Unit = lock.synchronized {
    ensureActive()
    if (!indexingJobs.contains(project)) {
      val jobOpt = createIndexJob(project)
      jobOpt.foreach { job =>
        job.schedule()
        indexingJobs.put(project, job)
      }
    }
  }

  def stopIndexing(project: IProject): Unit = lock.synchronized {
    ensureActive()
    indexingJobs.get(project).foreach { job =>
      job.cancel()
      indexingJobs.remove(project)
    }
  }

  def isIndexing(project: IProject): Boolean = lock.synchronized {
    ensureActive()
    indexingJobs.contains(project)
  }

  private def createIndexJob(project: IProject): Option[ProjectIndexJob] = lock.synchronized {
    ensureActive()

    val onStopped = (job: ProjectIndexJob) => {
      // If the job, for some reason, stops we want to remove it.
      indexingJobs.remove(project)
      ()
    }

    ScalaPlugin.plugin.asScalaProject(project).map { sp =>
      ProjectIndexJob(index, sp, 5000, onStopped)
    }
  }

  private def ensureActive(): Unit = lock.synchronized {
    if(!active)
      throw new ScalaSearchException("Index manager is not started.")
  }
}