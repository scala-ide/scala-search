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
import org.scalaide.util.internal.Utils._
import org.eclipse.ui.handlers.HandlerUtil
import org.scalaide.ui.internal.editor.ScalaSourceFileEditor
import org.scala.tools.eclipse.search.searching.Location
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.Status
import org.eclipse.swt.widgets.Display
import org.scala.tools.eclipse.search.searching.ProjectFinder
import org.scalaide.core.IScalaPlugin
import org.scala.tools.eclipse.search.searching.Scope

class OpenTypeHierarchy
  extends AbstractHandler
     with HasLogger {

  private val finder: Finder = SearchPlugin.finder
  private val reporter: ErrorReporter = new DialogErrorReporter

  override def execute(event: ExecutionEvent): Object = {

    for {
      // For the selection
      editor      <- Option(HandlerUtil.getActiveEditor(event)) onEmpty reporter.reportError("An editor has to be active")
      scalaEditor <- editor.asInstanceOfOpt[ScalaSourceFileEditor] onEmpty reporter.reportError("Active editor wasn't a Scala editor")
      selection   <- UIUtil.getSelection(scalaEditor) onEmpty reporter.reportError("You need to have a selection")
    } {
      // Get the relevant scope to search for sub-types in.
      val projects = ProjectFinder.projectClosure(scalaEditor.getInteractiveCompilationUnit.scalaProject.underlying)
      val scope = Scope(projects.map(IScalaPlugin().asScalaProject(_)).flatten)
      // Get the entity of the given location and set it as the root of the type-hierarchy.
      val loc = Location(scalaEditor.getInteractiveCompilationUnit, selection.getOffset())
      // Find the entity and open the view if appropriate
      finder.entityAt(loc) match {
        case Right(Some(entity: TypeEntity)) => showTypeHierarchyView(entity, scope)
        case Right(Some(entity)) => reporter.reportError(s"Sorry, can't use selected '${entity.name}' to build a type-hierarchy.")
        case Right(None) => // No-op
        case Left(_) =>
          reporter.reportError("Sorry, couldn't get the symbol of the given entity.\n\n" +
                               "This is very likely a bug, so please submit a bug report that contains\n"+
                               "a minimal example to https://www.assembla.com/spaces/scala-ide/tickets\n\n" +
                               "Thank you! - IDE Team")
      }
    }

    def showTypeHierarchyView(e: TypeEntity, scope: Scope): Unit = {

      val view = PlatformUI.getWorkbench()
                 .getActiveWorkbenchWindow()
                 .getActivePage()
                 .showView(TypeHierarchyView.VIEW_ID)

      for {
        view <- Option(view) onEmpty logger.debug("Couldn't get view")
        thview <- view.asInstanceOfOpt[TypeHierarchyView] onEmpty logger.debug("Wasn't an instance of TypeHierarchyView")
      } {
        thview.clear()
        thview.setRoot(e, scope)
      }
    }

    // According to the Eclipse docs we have to return null.
    null
  }

}