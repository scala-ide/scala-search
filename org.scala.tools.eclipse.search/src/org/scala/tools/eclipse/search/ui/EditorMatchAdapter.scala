package org.scala.tools.eclipse.search.ui

import org.eclipse.search.ui.text.IEditorMatchAdapter
import org.eclipse.search.ui.text.AbstractTextSearchResult
import org.eclipse.ui.IEditorPart
import org.eclipse.search.ui.text.Match
import org.scala.tools.eclipse.search.searching.Location
import org.eclipse.ui.IFileEditorInput
import org.scala.tools.eclipse.search.searching.Hit
import scala.tools.eclipse.logging.HasLogger
import org.scala.tools.eclipse.search.searching.Confidence

class EditorMatchAdapter extends IEditorMatchAdapter with HasLogger {

  // Returns all matches that are contained in the element shown in the given editor.
  override def computeContainedMatches(result: AbstractTextSearchResult, editor: IEditorPart): Array[Match] = {
    val hits = result.getElements.map(_.asInstanceOf[Confidence[Hit]])
    editor.getEditorInput() match {
      case in: IFileEditorInput => MatchAdatperHelper.matches(hits, in.getFile)
      case _ => Array()
    }
  }

  // Determines whether a match should be displayed in the given editor.
  override def isShownInEditor(m: Match, editor: IEditorPart) = {
    (m.getElement(), editor.getEditorInput()) match {
      case (hit: Hit, in: IFileEditorInput) => MatchAdatperHelper.existsAndMatches(hit, in.getFile)
      case _ => false
    }
  }
}