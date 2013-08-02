package org.scala.tools.eclipse.search

import scala.collection.mutable
import scala.tools.eclipse.logging.HasLogger
import scala.tools.eclipse.ScalaPlugin
import org.eclipse.core.resources.IProject
import org.scala.tools.eclipse.search.indexing.Index
import org.scala.tools.eclipse.search.indexing.SourceIndexer
import org.scala.tools.eclipse.search.jobs.ProjectIndexJob
import org.eclipse.core.resources.IFile
import java.util.concurrent.LinkedBlockingQueue
import org.eclipse.core.runtime.jobs.JobChangeAdapter
import org.eclipse.core.runtime.jobs.IJobChangeEvent

/**
 * Responsible for keeping track of the various indexing jobs. It uses
 * ProjectChangeObserver to keep track of Resource Events related to
 * projects.
 *
 * In fixed intervals it starts a `ProjectIndexJob` for the relevant
 * project if any files have been changed.
 *
 * @note This trait is thread-safe.
 */
class IndexJobManager(indexer: SourceIndexer) extends Lifecycle with HasLogger {

  protected val lock = new Object

  /** Contains the changed files that haven't yet been indexed.
   *
   *  @note Potentially changed by several threads. This instance,
   *  all of the `FileChangeObserver`s and the Scheduler thread.
   */
  protected val changedResources =
    new LinkedBlockingQueue[(IProject, IFile, FileEvent)]()

  /** Keeps track of currently running indexing job for each
   *  Scala project.
   *
   *  @note Guarded by `lock`.
   */
  private val runningJobs =
    new mutable.HashMap[IProject, ProjectIndexJob]

  /** Keeps track of of the FileChangeObservers
   *  @note Guarded by `lock`.
   */
  private val fileChangedObservers =
    new mutable.HashMap[IProject, Observing]

  /** Has the manager been started?
   *  @note Guarded by `lock`.
   */
  private var active: Boolean = false

  /** Listener for reacting to project's open/closed/deleted events.
   *  @note Guarded by `lock`.
   */
  private var observer: Observing = _

  /** Responsible for scheduling ProjectIndexJob's in fixed intervals
   *  if needed
   *
   *  @note Guarded by `lock`.
   */
  private var scheduler: Thread = _

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
      onOpen = startTrackingChanges(_),
      onNewScalaProject = startTrackingChanges(_),
      onClose = stopTrackingChanges(_),
      onDelete = project => {
        stopTrackingChanges(project)
        if(indexer.index.indexExists(project))
          indexer.index.deleteIndex(project)
      })

    scheduler = new Thread(new Scheduler())
    scheduler.start()
  }

  override def shutdown() = lock.synchronized {
    ensureActive()
    observer.stop()
    observer = null
    fileChangedObservers.keys.foreach(stopTrackingChanges)
    fileChangedObservers.clear
    changedResources.clear
    scheduler.stop()
    scheduler = null
    active = false
  }

  /**
   * Starts tracking file changes and indexes any files that haven't
   * been indexed yet.
   */
  def startTrackingChanges(project: IProject): Unit = lock.synchronized {
    ensureActive()
    if (!fileChangedObservers.contains(project)) {
      startObservingChanges(project)
      startIndexing(project, Nil)
    }
  }

  /**
   * Stop tracking file changes for the given project.
   */
  def stopTrackingChanges(project: IProject): Unit = lock.synchronized {
    ensureActive()
    fileChangedObservers.get(project) foreach { observer =>
      observer.stop
      fileChangedObservers.remove(project)
    }
    runningJobs.get(project) foreach { job =>
      job.cancel()
      runningJobs.remove(project)
    }
  }

  def isTrackingChanges(project: IProject): Boolean = lock.synchronized {
    ensureActive()
    fileChangedObservers.contains(project)
  }

  def isCurrentlyIndexing(project: IProject): Boolean = lock.synchronized {
    ensureActive()
    // Since the ProjectIndexJob is removed from `runningJobs` when
    // it's finished, we know that if the map contains a job for the
    // project then it's indexing files in that project at the moment.
    runningJobs.contains(project)
  }

  private def startIndexing(project: IProject, changeset: Seq[(IFile, FileEvent)]): Unit = lock.synchronized {
    ensureActive()
    (for {
      p <- Option(project)
      plugin <- Option(ScalaPlugin.plugin)
      sp <- ScalaPlugin.plugin.asScalaProject(project)
    } yield {
      val job = ProjectIndexJob(indexer, sp, changeset)
      job.addJobChangeListener(new JobChangeAdapter {
        override def done(event: IJobChangeEvent): Unit = lock.synchronized {
          logger.debug("A job finished for " + project.getName)
          runningJobs.remove(project)
        }
      })
      runningJobs.put(project, job)
      job.schedule
    }) getOrElse {
       logger.debug(s"Wasn't able to start indexing job for ${project.getName}" +
                     "as it wasn't a ScalaProject")
    }
  }

  private def startObservingChanges(project: IProject): Unit = {
    ensureActive()
    (for {
      p <- Option(project)
      plugin <- Option(ScalaPlugin.plugin)
      sp <- ScalaPlugin.plugin.asScalaProject(project)
    } yield {
      val observer = FileChangeObserver(sp)(
        onChanged = f => lock.synchronized {
          if (indexer.index.isIndexable(f)) changedResources put (project, f, Changed)
        },
        onAdded   = f => lock.synchronized {
          if (indexer.index.isIndexable(f)) changedResources put (project, f, Added)
        },
        onRemoved = f => lock.synchronized {
          if (indexer.index.supportedFileExtension(f)) changedResources put (project, f, Removed)
        }
      )
      fileChangedObservers.put(project, observer)
    }) getOrElse {
       logger.debug(s"Wasn't able to start indexing observing chnages for ${project.getName} " +
                     "as it wasn't a ScalaProject")
    }
  }

  private def ensureActive(): Unit = lock.synchronized {
    if(!active)
      throw new ScalaSearchException("Index manager is not started.")
  }


  private def getChangesetByProject: Map[IProject, Seq[(IProject, IFile, FileEvent)]] = {
    var changes = List[(IProject, IFile, FileEvent)]()
    while (!changedResources.isEmpty) {
      changes = changedResources.poll :: changes
    }

    changes.groupBy {
      case (project, file, event) => project
    }
  }

  /**
   * Small Runnable that takes care of scheduling new ProjectIndexJob's
   */
  private class Scheduler extends Runnable {

    override def run: Unit = loop

    private def loop: Unit = {
      Thread.sleep(1000)

      lock.synchronized {
        getChangesetByProject foreach { case (project, xs) =>
          if (isCurrentlyIndexing(project)) {
            logger.debug(s"A job for ${project.getName} is already running. Waiting till it's finished to add change-set ${xs}")
            // If there is already an indexing job running for this
            // project push the changeset for this project back in the
            // queue and they'll be indexed next time around.
            xs foreach ( x => changedResources.put(x) )
          } else {
            val changeSet = xs map ( triple => (triple._2, triple._3) )
            logger.debug("Start indexing change-set " + changeSet)
            startIndexing(project, changeSet)
          }
        }
      }

      loop
    }
  }

}