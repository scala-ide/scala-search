package org.scala.tools.eclipse.search.ui

import org.eclipse.search.ui.text.IFileMatchAdapter
import org.eclipse.search.ui.text.AbstractTextSearchResult
import org.eclipse.core.resources.IFile
import org.eclipse.search.ui.text.Match
import org.scala.tools.eclipse.search.searching.Location
import org.scala.tools.eclipse.search.searching.Hit

class FileMatchAdapter extends IFileMatchAdapter {

  // Returns an array with all matches contained in the given file in the given search result.
  def computeContainedMatches(result: AbstractTextSearchResult, file: IFile): Array[Match] = {
    val hits = result.getElements.map(_.asInstanceOf[Hit])
    MatchAdatperHelper.matches(hits, file)
  }

  // Returns the file associated with the given element (usually the file the element is contained in).
  def getFile(element: Object): IFile = {
    //  Doc: If the element is not associated with a file, 
    //       this method should return null.
    //
    //  So we return null if
    //    - We don't recognize the object
    //    - The file has been deleted since the search was executed or
    //      the project that contains the file is now closed.
    element match {
      case hit: Hit => MatchAdatperHelper.getWorkspaceFile(hit).getOrElse(null)
      case _ => null
    }
  }

}