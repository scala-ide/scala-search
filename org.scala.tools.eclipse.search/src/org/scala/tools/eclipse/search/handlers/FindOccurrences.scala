package org.scala.tools.eclipse.search
package handlers

import org.scalaide.logging.HasLogger
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
import org.scala.tools.eclipse.search.searching.Finder
import org.scala.tools.eclipse.search.searching.Location
import org.scala.tools.eclipse.search.searching.Hit
import org.scala.tools.eclipse.search.ui.DialogErrorReporter
import org.scala.tools.eclipse.search.ui.SearchResult
import org.scala.tools.eclipse.search.searching.SearchPresentationCompiler
import org.scala.tools.eclipse.search.indexing.SearchFailure
import org.scalaide.util.Utils.WithAsInstanceOfOpt
import org.scala.tools.eclipse.search.searching.Certain
import org.scala.tools.eclipse.search.searching.Uncertain
import org.scala.tools.eclipse.search.searching.Confidence
import org.eclipse.search.ui.text.Match
import org.scalaide.core.IScalaProject
import org.scala.tools.eclipse.search.searching.ProjectFinder
import org.scalaide.core.IScalaPlugin
import org.scala.tools.eclipse.search.searching.Scope
import org.scalaide.ui.editor.InteractiveCompilationUnitEditor

class FindOccurrences
  extends AbstractHandler
     with HasLogger {

  private val finder: Finder = SearchPlugin.finder
  private val reporter: ErrorReporter = new DialogErrorReporter

  override def execute(event: ExecutionEvent): Object = {
    for {
      editor      <- Option(HandlerUtil.getActiveEditor(event)) onEmpty reporter.reportError("An editor has to be active")
      scalaEditor <- editor.asInstanceOfOpt[InteractiveCompilationUnitEditor] onEmpty reporter.reportError("Active editor wasn't a Scala editor")
      selection   <- UIUtil.getSelection(scalaEditor) onEmpty reporter.reportError("You need to have a selection")
    } {
      scalaEditor.getInteractiveCompilationUnit.withSourceFile { (_, pc) =>
        // Get the relevant scope to search for sub-types in.
        val scope = ProjectFinder.projectClosure(scalaEditor.getInteractiveCompilationUnit.scalaProject.underlying)
        val scalaScope = Scope(scope.map(IScalaPlugin().asScalaProject(_)).flatten)
        // Get the entity at the given location and start a search in the right scope
        val spc = new SearchPresentationCompiler(pc)
        val loc = Location(scalaEditor.getInteractiveCompilationUnit, selection.getOffset())
        spc.entityAt(loc).right.toOption.flatten map { entity =>
          if (entity.canFindReferences) {
            startSearch(entity, scalaScope)
          } else reporter.reportError("Sorry, that kind of entity isn't supported yet.")
        } getOrElse(reporter.reportError("Couldn't recognize the enity of the selection"))
      }
    }

    null // According to the Eclipse docs we have to return null.
  }

  private def startSearch(entity: Entity, scope: Scope): Unit = {
    NewSearchUI.runQueryInBackground(new ISearchQuery(){

      val sr = new SearchResult(this)

      @volatile var hitsCount = 0
      @volatile var potentialHitsCount = 0

      override def canRerun(): Boolean = false
      override def canRunInBackground(): Boolean = true

      override def getLabel: String =
        s"'${entity.name}' - ${hitsCount} exact matches, $potentialHitsCount potential matches. Total ${hitsCount+potentialHitsCount}"

      override def run(monitor: IProgressMonitor): IStatus = {
        finder.occurrencesOfEntityAt(entity, scope, monitor)(
            handler = (h: Confidence[Hit]) => h match {
              case Certain(hit) =>
                hitsCount += 1
                sr.addMatch(new Match(h, hit.offset, hit.word.length))
              case Uncertain(hit) =>
                potentialHitsCount  += 1
                sr.addMatch(new Match(h, hit.offset, hit.word.length))
            },
            errorHandler = (fail: SearchFailure) => {
              logger.debug(s"Got an error ${fail}")
            })
        Status.OK_STATUS
      }
      override def getSearchResult(): ISearchResult = sr
    })
  }
}