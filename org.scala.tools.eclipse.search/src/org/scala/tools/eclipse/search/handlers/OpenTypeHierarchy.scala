package org.scala.tools.eclipse.search
package handlers

import scala.tools.eclipse.logging.HasLogger
import org.eclipse.core.commands.AbstractHandler
import org.eclipse.core.commands.ExecutionEvent
import org.eclipse.ui.PlatformUI
import org.scala.tools.eclipse.search.ErrorReporter
import org.scala.tools.eclipse.search.SearchPlugin
import org.scala.tools.eclipse.search.searching.Finder
import org.scala.tools.eclipse.search.ui.DialogErrorReporter
import org.scala.tools.eclipse.search.ui.TypeHierarchyView
import scala.tools.eclipse.util.Utils._
import org.eclipse.ui.handlers.HandlerUtil
import scala.tools.eclipse.ScalaSourceFileEditor
import org.scala.tools.eclipse.search.searching.Location
import org.scala.tools.eclipse.search.searching.SearchPresentationCompiler
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.Status
import org.eclipse.swt.widgets.Display

class OpenTypeHierarchy
  extends AbstractHandler
     with HasLogger {

  private val finder: Finder = SearchPlugin.finder
  private val reporter: ErrorReporter = new DialogErrorReporter

  override def execute(event: ExecutionEvent): Object = {

    val view = PlatformUI.getWorkbench()
                 .getActiveWorkbenchWindow()
                 .getActivePage()
                 .showView(TypeHierarchyView.VIEW_ID)

    for {
      // For the view
      view <- Option(view) onEmpty logger.debug("Couldn't get view")
      thview <- view.asInstanceOfOpt[TypeHierarchyView] onEmpty logger.debug("Wasn't an instance of TypeHierarchyView")
      // For the selection
      editor      <- Option(HandlerUtil.getActiveEditor(event)) onEmpty reporter.reportError("An editor has to be active")
      scalaEditor <- editor.asInstanceOfOpt[ScalaSourceFileEditor] onEmpty reporter.reportError("Active editor wasn't a Scala editor")
      selection   <- UIUtil.getSelection(scalaEditor) onEmpty reporter.reportError("You need to have a selection")
    } {
      thview.clear()
      val loc = Location(scalaEditor.getInteractiveCompilationUnit, selection.getOffset())
      finder.entityAt(loc) map {
        case x: TypeEntity => thview.setRoot(x)
        case x: Entity => reporter.reportError("Sorry, can't use selected entity to build a type-hierarchy")
      } getOrElse {
        logger.debug("Couldn't get entity")
      }
    }

    // According to the Eclipse docs we have to return null.
    null
  }

}