package org.scala.tools.eclipse.search.jobs

import org.eclipse.core.resources.WorkspaceJob
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.resources.IResourceChangeListener
import org.eclipse.core.resources.IResourceChangeEvent
import org.eclipse.core.resources.IResourceDeltaVisitor
import org.eclipse.core.resources.IResourceDelta
import org.eclipse.core.resources.IResource
import org.eclipse.core.resources.IFile
import org.scala.tools.eclipse.search.SearchPlugin
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.Status
import scala.collection.concurrent.Map
import scala.collection.concurrent.TrieMap
import scala.tools.eclipse.logging.HasLogger
import org.scala.tools.eclipse.search.indexing.SourceIndexer

/**
 * Asynchronous background jobs that periodically updates the index.
 * 
 * This job is started when the plugin is loaded and it then periodically
 * schedules itself for execution. It records changes to the workspace through
 * the IResourceChangeListener methods and stages the changes for indexing in
 * `changedResources`. When the job is executed it iterates over the changes
 * and applies them to the index before resetting `changedResources`.
 *
 */
class UpdateIndexJob(indexer: SourceIndexer, interval: Long) extends WorkspaceJob("Update the index")
                                                                with IResourceChangeListener
                                                                with HasLogger {

  private var changedResources: Map[IResource, Integer] = TrieMap[IResource, Integer]() // changed by several threads
  ResourcesPlugin.getWorkspace().addResourceChangeListener(this, IResourceChangeEvent.POST_CHANGE) // listen to file changes

  /**
   * Invoked by Eclipse whenever a resource in the workspace chagnes.
   */
  override def resourceChanged(event: IResourceChangeEvent): Unit = {
    event.getType match {
      case IResourceChangeEvent.POST_CHANGE => event.getDelta().accept(visitor, false)
      case _ => // don't care about the other events
    }
  }

  def runInWorkspace(monitor: IProgressMonitor): IStatus = {

    if (SearchPlugin.isInitialIndexRunning) {
      // If the initial indexing job isn't done we don't want to update the index.
      logger.debug("Initial workspace index isn't done yet. Waiting.")
      schedule(interval)
      return Status.CANCEL_STATUS
    }

    if (monitor.isCanceled()) {
      return Status.CANCEL_STATUS
    }

    logger.debug("Updating Scala Search Index.")
    monitor.beginTask("Updating Scala Search Index", changedResources.size)
    var returnStatus = Status.OK_STATUS

    val it: Iterator[(IResource, Integer)] = changedResources.iterator
    while( it.hasNext && !monitor.isCanceled()) {
      val (resource, typ) = it.next
      monitor.subTask(resource.getName())
      resource.getType match {
        case IResource.FILE  => indexer.indexIFile(resource.asInstanceOf[IFile])
        case _ =>
      }
      changedResources.remove(resource, typ)
      monitor.worked(1)
    }
    monitor.done()
    schedule(interval)
    returnStatus
  }

  // Traverses the changes and records the one we're interested in
  private def visitor: IResourceDeltaVisitor = new IResourceDeltaVisitor {
    def visit(delta: IResourceDelta): Boolean = {
      val resource = delta.getResource
      resource.getType match {
        case IResource.FILE => {
          val file = resource.asInstanceOf[IFile]
          if (SearchPlugin.isIndexable(file)) {
            changedResources.put( file, file.getType())
          }
        }
        case _ => // only care about changed files atm.
      }
      true
    }
  }

}