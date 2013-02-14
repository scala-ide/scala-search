package org.scala.tools.eclipse.search.ui

import org.eclipse.jface.resource.ImageDescriptor
import org.eclipse.search.ui.ISearchQuery
import org.eclipse.search.ui.text.AbstractTextSearchResult
import org.eclipse.search.ui.text.IEditorMatchAdapter
import org.eclipse.search.ui.text.IFileMatchAdapter

/**
 * Represents the result of executing a search query against Scala
 * files.
 */
class SearchResult extends AbstractTextSearchResult {

  /**
   * An implementation of IEditorMatchAdapter appropriate for this search result.
   */
  def getEditorMatchAdapter(): IEditorMatchAdapter = ???

  /**
   * An implementation of IFileMatchAdapter appropriate for this search result.
   */
  def getFileMatchAdapter(): IFileMatchAdapter = ???

  /**
   * The image descriptor for the given ISearchResult.
   */
  def getImageDescriptor(): ImageDescriptor = ???

  /**
   * A user readable label for this search result. The label is typically used in the result view
   * and should contain the search query string and number of matches.
   */
  def getLabel(): String = "Haven't implemented yet"

  /**
   * The query that produced this search result.
   */
  def getQuery(): ISearchQuery = ???

  /**
   * A tooltip to be used when this search result is shown in the UI.
   */
  def getTooltip(): String = "Haven't implemented yet"

}