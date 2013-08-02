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
import org.scala.tools.eclipse.search.Observing
import org.scala.tools.eclipse.search.FileEvent
import org.scala.tools.eclipse.search.Changed
import org.scala.tools.eclipse.search.Added
import org.scala.tools.eclipse.search.Removed

/**
 * Jobs that indexes the source files of a given project.
 *
 * The ProjectIndexJob does not lock the workspace while running. We don't need
 * to as any changes made to the workspace during indexing will eventually be
 * reported to the IndexJobManager through the events sent by Eclipse and the
 * index will be updated accordingly.
 */
class ProjectIndexJob private (
  indexer: SourceIndexer,
  project: ScalaProject,
  changeset: Seq[(IFile, FileEvent)]
) extends Job("Project Indexing Job: " + project.underlying.getName) with HasLogger {

  private def projectIsOpenAndExists: Boolean = {
    project.underlying.exists() && project.underlying.isOpen
  }

  override def run(monitor: IProgressMonitor): IStatus = {

    if (monitor.isCanceled()) {
      return Status.CANCEL_STATUS
    }

    val shouldIndexEverything =
      Option(project) map (p => !indexer.index.indexExists(p.underlying))

    if (shouldIndexEverything.getOrElse(false)) {
      logger.debug("No prior index exists so indexing the entire project: " + project.underlying.getName)
      indexer.indexProject(project).recover(handlers)
    }

    val it = changeset.iterator
    while( it.hasNext && !monitor.isCanceled() && projectIsOpenAndExists) {
      val (file, changed) = it.next
      monitor.subTask(file.getName())
      changed match {
        case Changed => indexer.indexIFile(file).recover(handlers)
        case Added   => indexer.indexIFile(file).recover(handlers)
        case Removed => indexer.index.removeOccurrencesFromFile(file.getProjectRelativePath(), project).recover(handlers)
      }
      monitor.worked(1)
    }

    if (it.hasNext) {
      val rest = for (i <- it) yield i
      if (monitor.isCanceled()) {
        logger.debug(s"Didn't index ${rest.toList} as the job was canceled")
      } else {
        logger.debug(s"Didn't index ${rest.toList} as the project was closed")
      }
    }

    monitor.done()
    Status.OK_STATUS
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
    // For all other exceptions we delete the index re-index the entire project
    case otherwise =>
      logger.debug(s"The index was broken so we delete it and re-index the project ${project.underlying.getName}")
      cancel()
      indexer.index.deleteIndex(project.underlying)
      schedule(1000)
  }
}

object ProjectIndexJob extends HasLogger {

  def apply(indexer: SourceIndexer,
                sp: ScalaProject,
         changeset: Seq[(IFile, FileEvent)]): ProjectIndexJob = {
    val job = new ProjectIndexJob(indexer, sp, changeset)
    job.setPriority(Job.LONG)
    job
  }

}