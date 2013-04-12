package org.scala.tools.eclipse.search.jobs

import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import scala.tools.eclipse.ScalaProject
import scala.tools.eclipse.logging.HasLogger
import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.resources.WorkspaceJob
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.Status
import org.eclipse.core.runtime.jobs.Job
import org.scala.tools.eclipse.search.FileChangeObserver
import org.scala.tools.eclipse.search.SearchPlugin
import org.scala.tools.eclipse.search.indexing.OccurrenceCollector.InvalidPresentationCompilerException
import org.scala.tools.eclipse.search.indexing.SourceIndexer
import org.scala.tools.eclipse.search.indexing.Index
import org.scala.tools.eclipse.search.indexing.SourceIndexer.UnableToIndexFilesException

/**
 * Background jobs that indexes the source files of a given project.
 *
 * The continuously schedules itself for execution, and as such it will always
 * be running as long as Eclipse is running.
 *
 * It uses a FileChangeObserver to keep track of files in the project that
 * needs to be re-indexed.
 */
class ProjectIndexJob private (
    indexer: SourceIndexer,
    index: Index,
    project: ScalaProject,
    interval: Long) extends WorkspaceJob("Project Indexing Job: " + project.underlying.getName) with HasLogger {

  private trait FileEvent
  private case object Added extends FileEvent
  private case object Changed extends FileEvent
  private case object Removed extends FileEvent

  // Potentially changed by several threads. This job and the FileChangeObserver
  private val changedResources: BlockingQueue[(IFile, FileEvent)] = new LinkedBlockingQueue[(IFile, FileEvent)]

  private val changed = (f: IFile) => {
    if (SearchPlugin.isIndexable(f)) {
      changedResources.put(f, Changed)
    }
  }

  private val added = (f: IFile) => {
    if (SearchPlugin.isIndexable(f)) {
      changedResources.put(f, Added)
    }
  }

  private val removed = (f: IFile) => changedResources.put(f, Removed)

  private val observer = FileChangeObserver(project.underlying)(
    onChanged = changed,
    onAdded = added,
    onRemoved = removed
  )

  private def projectIsOpenAndExists: Boolean = {
    project.underlying.exists() && project.underlying.isOpen
  }

  override def runInWorkspace(monitor: IProgressMonitor): IStatus = {

    if (monitor.isCanceled()) {
      return Status.CANCEL_STATUS
    }

    val shouldIndex = for {
      proj <- Option(project)
      underlying <- Option(proj.underlying)
      plugin <- SearchPlugin.plugin
    } yield !plugin.indexLocationForProject(underlying).exists

    if (shouldIndex.getOrElse(false)) {
      indexer.indexProject(project).recover(handlers)
    }

    while( !changedResources.isEmpty && !monitor.isCanceled() && projectIsOpenAndExists) {
      val (file, changed) = changedResources.poll()
      monitor.subTask(file.getName())
      changed match {
        case Changed => indexer.indexIFile(file).recover(handlers)
        case Added   => indexer.indexIFile(file).recover(handlers)
        case Removed => index.removeOccurrencesFromFile(file.getProjectRelativePath(), file.getProject).recover(handlers)
      }
      monitor.worked(1)
    }
    monitor.done()

    if (projectIsOpenAndExists && !monitor.isCanceled()) {
      schedule(interval)
    } else {
      observer.stop
    }
    Status.OK_STATUS
  }

  /**
   * Stops the job, removes the index from disc and reschedules the job for execution. This will
   * make it re-index the entire project.
   */
  private def removeIndexAndRestart = {
    logger.debug(s"The index was broken so we delete it and re-index the project ${project.underlying.getName}")
    cancel() // Stop the current 'run' of the thread.
    SearchPlugin.plugin.foreach { plugin =>
      val folder = plugin.indexLocationForProject(project.underlying)
      folder.delete()
      schedule(interval)
    }
  }

  // Logic for how we deal with the various failures that can happen when indexing.
  private def handlers[A]: PartialFunction[Throwable, Unit] = {
    //
    // The presentation compiler is not working. There's no way we can recover
    // from this.
    case ex: InvalidPresentationCompilerException =>
      logger.error(ex)
      cancel()
    //
    // IOExceptions wile indexing some files. Simply log the error and let the job
    // keep running as usual.
    case ex: UnableToIndexFilesException =>
      ex.files.foreach { file =>
        logger.error(s"Failed to index file due to IOException: ${file}")
      }
    //
    // For all other exceptions we delete the index on disc and restart the indexing
    // job, thus re-indexing the entire project.
    case otherwise =>
      removeIndexAndRestart
  }
}

object ProjectIndexJob extends HasLogger {

  def apply(indexer: SourceIndexer, index: Index, sp: ScalaProject, interval: Int = 5000): Job = {

    logger.debug("Started ProjectIndexJob for " + sp.underlying.getName)

    val job = new ProjectIndexJob(indexer, index, sp, interval = interval)
    job.setRule(ResourcesPlugin.getWorkspace().getRuleFactory().modifyRule(sp.underlying))
    job.setPriority(Job.LONG)
    job
  }

}