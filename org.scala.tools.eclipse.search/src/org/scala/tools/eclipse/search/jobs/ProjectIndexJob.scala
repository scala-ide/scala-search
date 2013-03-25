package org.scala.tools.eclipse.search.jobs

import scala.tools.eclipse.logging.HasLogger
import org.eclipse.core.resources.WorkspaceJob
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.IStatus
import org.scala.tools.eclipse.search.indexing.SourceIndexer
import org.eclipse.core.resources.IWorkspaceRoot
import org.eclipse.core.runtime.Status
import org.scala.tools.eclipse.search.Util._
import org.eclipse.core.resources.IProject
import org.scala.tools.eclipse.search.FileChangeObserver
import org.eclipse.core.resources.IResource
import org.eclipse.core.resources.IFile
import scala.tools.eclipse.ScalaProject
import org.scala.tools.eclipse.search.SearchPlugin
import scala.collection.concurrent.TrieMap
import scala.collection.concurrent.Map
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import scala.tools.eclipse.ScalaPlugin
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.runtime.Platform

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
    project: ScalaProject,
    interval: Long) extends WorkspaceJob("Project Indexing Job: " + project.underlying.getName) with HasLogger {

  private trait FileEvent
  private case object Changed extends FileEvent
  private case object Removed extends FileEvent

  // Potentially changed by several threads. This job and the FileChangeObserver
  private val changedResources: BlockingQueue[(IFile, FileEvent)] = new LinkedBlockingQueue[(IFile, FileEvent)]

  private val changed = (f: IFile) => {
    if (SearchPlugin.isIndexable(f)) {
      changedResources.put(f, Changed)
    }
  }

  private val removed = (f: IFile) => changedResources.put(f, Removed)

  private val observer = FileChangeObserver(project.underlying)(
    onChanged = changed,
    onAdded = changed,
    onRemoved = removed
  )

  private def openAndExists: Boolean = {
    project.underlying.exists() && project.underlying.isOpen
  }

  override def runInWorkspace(monitor: IProgressMonitor): IStatus = {

    if (monitor.isCanceled()) {
      return Status.CANCEL_STATUS
    }

    if (!SearchPlugin.plugin.indexLocationForProject(project.underlying).exists) {
      indexer.indexProject(project)
    }

    while( !changedResources.isEmpty && !monitor.isCanceled() && openAndExists) {
      val (file, changed) = changedResources.poll()
      monitor.subTask(file.getName())
      changed match {
        case Changed => indexer.indexIFile(file)
        case Removed => // TODO: Remove from the index. This class needs access to the index for that.
      }
      monitor.worked(1)
    }
    monitor.done()

    if (openAndExists) {
      schedule(interval)
    } else {
      observer.stop
    }
    Status.OK_STATUS
  }
}

object ProjectIndexJob extends HasLogger {

  def apply(indexer: SourceIndexer, sp: ScalaProject): ProjectIndexJob = {

    logger.debug("Started ProjectIndexJob for " + sp.underlying.getName)

    val job = new ProjectIndexJob(indexer, sp, interval = 5000)
    job.setRule(ResourcesPlugin.getWorkspace().getRuleFactory().modifyRule(sp.underlying))
    job.setPriority(Job.LONG)
    job.schedule()
    job
  }

}