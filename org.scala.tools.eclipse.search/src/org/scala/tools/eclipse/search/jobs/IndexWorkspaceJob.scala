package org.scala.tools.eclipse.search.jobs

import scala.tools.eclipse.logging.HasLogger
import org.eclipse.core.resources.WorkspaceJob
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.IStatus
import org.scala.tools.eclipse.search.indexing.SourceIndexer
import org.eclipse.core.resources.IWorkspaceRoot
import org.eclipse.core.runtime.Status
import org.scala.tools.eclipse.search.Util._

/**
 * Asynchronous job that indexing the entire workspace. This is executed once
 * upon startup of Eclipse.
 */
class IndexWorkspaceJob(indexer: SourceIndexer, workspace: IWorkspaceRoot) extends WorkspaceJob("Initial Indexing Job") with HasLogger {

  override def runInWorkspace(monitor: IProgressMonitor): IStatus = {
    timed("initial indexing job") {
      indexer.indexWorkspace(workspace)
    }
    Status.OK_STATUS
  }

}