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
    val results = result.getElements.map(_.asInstanceOf[Hit])
    results.filter(_.cu.workspaceFile == file).map(_.toMatch)
  }

  // Returns the file associated with the given element (usually the file the element is contained in).
  def getFile(element: Object): IFile = {
    val location = element.asInstanceOf[Hit]
    location.cu.workspaceFile
  }

}