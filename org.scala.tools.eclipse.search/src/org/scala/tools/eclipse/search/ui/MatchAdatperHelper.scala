package org.scala.tools.eclipse.search.ui

import org.eclipse.core.resources.IFile
import org.eclipse.search.ui.text.Match
import org.scala.tools.eclipse.search.Util
import org.scala.tools.eclipse.search.searching.Confidence
import org.scala.tools.eclipse.search.searching.Hit

object MatchAdatperHelper {

  def matches(hits: Seq[Confidence[Hit]], file: IFile): Array[Match] = {
    hits filter { h =>
      existsAndMatches(h.value, file)
    } map { h =>
      new Match(h, h.value.offset, h.value.word.length)
    } toArray
  }

  def existsAndMatches(hit: Hit, file: IFile): Boolean = {
    (for {
      hitFile <- Util.getWorkspaceFile(hit.cu)
      if existsAndProjectIsOpen(hitFile) && existsAndProjectIsOpen(file)
    } yield hitFile == file) getOrElse false
  }

  def existsAndProjectIsOpen(file: IFile): Boolean = {
    // if the project is closed, file.exists will also return false
    file != null && file.exists
  }

}