package org.scala.tools.eclipse.search.ui

import org.eclipse.jface.resource.ImageDescriptor
import org.eclipse.search.ui.ISearchQuery
import org.eclipse.search.ui.text.AbstractTextSearchResult
import org.eclipse.search.ui.text.IEditorMatchAdapter
import org.eclipse.search.ui.text.IFileMatchAdapter
import org.scala.tools.eclipse.search.searching.Hit
import org.eclipse.search.ui.text.Match
import scala.tools.eclipse.ScalaImages
import org.scala.tools.eclipse.search.searching.Confidence
import org.scala.tools.eclipse.search.Util

/**
 * Represents the result of executing a search query against Scala
 * files.
 */
class SearchResult(query: ISearchQuery) extends AbstractTextSearchResult {

  /**
   * An implementation of IEditorMatchAdapter appropriate for this search result.
   */
  def getEditorMatchAdapter(): IEditorMatchAdapter = new EditorMatchAdapter

  /**
   * An implementation of IFileMatchAdapter appropriate for this search result.
   */
  def getFileMatchAdapter(): IFileMatchAdapter = new FileMatchAdapter

  /**
   * The image descriptor for the given ISearchResult.
   */
  def getImageDescriptor(): ImageDescriptor = ScalaImages.SCALA_FILE

  /**
   * A user readable label for this search result. The label is typically used in the result view
   * and should contain the search query string and number of matches.
   */
  def getLabel(): String = query.getLabel

  /**
   * The query that produced this search result.
   */
  def getQuery(): ISearchQuery = query

  /**
   * A tooltip to be used when this search result is shown in the UI.
   */
  def getTooltip(): String = query.getLabel

  def resultsGroupedByFile: Map[String, Array[Confidence[Hit]]] = {
    // TODO: Cache this, for speed improvements
    val res = this.getElements.map(_.asInstanceOf[Confidence[Hit]])

    res.filter( x => Util.getWorkspaceFile(x.value.cu).isDefined)
       .groupBy(_.value.cu.workspaceFile.getProjectRelativePath().toOSString)
  }

}