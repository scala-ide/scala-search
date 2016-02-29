package org.scala.tools.eclipse.search
package handlers

import org.scalaide.logging.HasLogger
import org.eclipse.core.commands.AbstractHandler
import org.eclipse.core.commands.ExecutionEvent
import org.eclipse.ui.PlatformUI
import org.scala.tools.eclipse.search.ErrorReporter
import org.scala.tools.eclipse.search.SearchPlugin
import org.scala.tools.eclipse.search.searching.Finder
import org.scala.tools.eclipse.search.ui.DialogErrorReporter
import org.scala.tools.eclipse.search.ui.TypeHierarchyView
import org.scalaide.util.Utils.WithAsInstanceOfOpt
import org.eclipse.ui.handlers.HandlerUtil
import org.scala.tools.eclipse.search.searching.Location
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.Status
import org.eclipse.swt.widgets.Display
import org.scala.tools.eclipse.search.searching.ProjectFinder
import org.scalaide.core.IScalaPlugin
import org.scala.tools.eclipse.search.searching.Scope
import org.scalaide.ui.editor.InteractiveCompilationUnitEditor
import org.scala.tools.eclipse.search.ui.CallHierarchyView
import org.scala.tools.eclipse.search.ui.Node
import org.eclipse.jface.operation.IRunnableWithProgress
import org.eclipse.jface.dialogs.ProgressMonitorDialog
import scala.tools.nsc.interactive.Response
import scala.reflect.internal.util.OffsetPosition
import org.scala.tools.eclipse.search.ui.CallerNode

class OpenCallHierarchy extends AbstractHandler with HasLogger {
  private val finder: Finder = SearchPlugin.finder
  private val reporter: ErrorReporter = new DialogErrorReporter

  override def execute(event: ExecutionEvent): Object = {

    def scheduleJob(e: Entity, scope: Scope, in: Node) = {
      val job = new Job("Searching for callers...") {
        def run(monitor: IProgressMonitor): IStatus = {

          finder.findCallers(e, scope, (offset, ee, label, owner, project) => in.list = CallerNode(offset, ee, scope, label, owner, project) :: in.list, monitor)
          Status.OK_STATUS
        }
      }
      job.schedule()

    }

    def view = PlatformUI.getWorkbench()
      .getActiveWorkbenchWindow()
      .getActivePage()
      .showView(CallHierarchyView.VIEW_ID)

    for {
      // For the selection
      editor <- Option(HandlerUtil.getActiveEditor(event)) onEmpty reporter.reportError("An editor has to be active")
      scalaEditor <- editor.asInstanceOfOpt[InteractiveCompilationUnitEditor] onEmpty reporter.reportError("Active editor wasn't a Scala editor")
      selection <- UIUtil.getSelection(scalaEditor) onEmpty reporter.reportError("You need to have a selection")
      thview <- view.asInstanceOfOpt[CallHierarchyView] onEmpty logger.debug("Wasn't an instance of CallHierarchyView")
    } {
      // Get the relevant scope to search for sub-types in.
      val projects = ProjectFinder.projectClosure(scalaEditor.getInteractiveCompilationUnit.scalaProject.underlying)
      val scope = Scope(projects.map(IScalaPlugin().asScalaProject(_)).flatten)
      val loc = Location(scalaEditor.getInteractiveCompilationUnit, selection.getOffset())

      val root = thview.input
      root.list = List()
      finder.entityAt(loc) match {
        case Right(Some(entity: Method)) =>
          scheduleJob(entity, scope, root)
        case Right(Some(entity: Val)) =>
          scheduleJob(entity, scope, root)
        case Right(Some(entity: Var)) =>
          scheduleJob(entity, scope, root)
        case Right(Some(entity)) => reporter.reportError(s"Sorry, can't use selected '${entity.name}' to build a call-hierarchy.")
        case Right(None) => // No-op
        case Left(_) =>
          reporter.reportError("""Couldn't get the symbol of the given entity.
            |This is very likely a bug, so please submit a bug report that contains
            |a minimal example to https://www.assembla.com/spaces/scala-ide/tickets
            |Thank you! - IDE Team""".stripMargin)
      }
    }

    null
  }

}