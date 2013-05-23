package org.scala.tools.eclipse.search
package handlers

import scala.tools.eclipse.logging.HasLogger
import org.eclipse.core.commands.AbstractHandler
import org.eclipse.core.commands.ExecutionEvent
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.Status
import org.eclipse.search.ui.ISearchQuery
import org.eclipse.search.ui.ISearchResult
import org.eclipse.search.ui.NewSearchUI
import org.eclipse.ui.handlers.HandlerUtil
import org.scala.tools.eclipse.search.ErrorHandlingOption
import org.scala.tools.eclipse.search.SearchPlugin
import org.scala.tools.eclipse.search.UIUtil
import org.scala.tools.eclipse.search.indexing.Index
import org.scala.tools.eclipse.search.searching.Finder
import org.scala.tools.eclipse.search.searching.Location
import org.scala.tools.eclipse.search.searching.Result
import org.scala.tools.eclipse.search.ui.DialogErrorReporter
import org.scala.tools.eclipse.search.ui.SearchResult
import org.scala.tools.eclipse.search.searching.SearchPresentationCompiler
import org.scala.tools.eclipse.search.indexing.SearchFailure
import scala.tools.eclipse.util.Utils.any2optionable
import scala.tools.eclipse.ScalaSourceFileEditor

class FindOccurrencesOfMethod
  extends AbstractHandler
     with HasLogger {

  private val finder: Finder = SearchPlugin.finder
  private val reporter: ErrorReporter = new DialogErrorReporter

  override def execute(event: ExecutionEvent): Object = {
    for {
      editor      <- Option(HandlerUtil.getActiveEditor(event)) onEmpty reporter.reportError("An editor has to be active")
      scalaEditor <- editor.asInstanceOfOpt[ScalaSourceFileEditor] onEmpty reporter.reportError("Active editor wasn't a Scala editor")
      selection   <- UIUtil.getSelection(scalaEditor) onEmpty reporter.reportError("You need to have a selection")
    } {
      val loc = Location(scalaEditor.getInteractiveCompilationUnit, selection.getOffset())

      val (name, isMethod) = scalaEditor.getInteractiveCompilationUnit.withSourceFile { (_, pc) =>
        val spc = new SearchPresentationCompiler(pc)
        (spc.nameOfEntityAt(loc), spc.isMethod(loc))
      }(None, false)

      // Only supports methods for now.
      if (isMethod) {
        NewSearchUI.runQueryInBackground(new ISearchQuery(){

          val sr = new SearchResult(this)

          @volatile var hitsCount = 0
          @volatile var potentialHitsCount = 0

          override def canRerun(): Boolean = false
          override def canRunInBackground(): Boolean = true

          override def getLabel: String =
            s"'${name.getOrElse("selection")}' - ${hitsCount} exact matches, $potentialHitsCount potential matches "

          override def run(monitor: IProgressMonitor): IStatus = {
            finder.occurrencesOfEntityAt(loc)(
                hit = (r: Result) => {
                  hitsCount += 1
                  sr.addMatch(r.toMatch)
                },
                potentialHit = _ =>
                  potentialHitsCount  += 1,
                errorHandler = (fail: SearchFailure) =>
                  logger.debug(s"Got an error ${fail}"))
            Status.OK_STATUS
          }
          override def getSearchResult(): ISearchResult = sr
        })
      } else reporter.reportError("Sorry, find occurrences only supports methods for now")
    }
    // According to the Eclipse docs we have to return null.
    null
  }

}