package org.scala.tools.eclipse.search.ui

import org.eclipse.jface.viewers.TableViewer
import org.eclipse.jface.viewers.TreeViewer
import org.eclipse.search.ui.text.AbstractTextSearchViewPage

/**
 * The page that is responsible for displaying the results of executing
 * a scala search query.
 */
class SearchResultPage extends AbstractTextSearchViewPage {

  /**
   *  This method is called whenever all elements have been removed from the view.
   */
  def clear(): Unit = ???

  /**
   *  This method is called whenever the set of matches for the given elements changes.
   */
  def elementsChanged(elements: Array[Object]): Unit = ???

  /**
   * Invoked if the results are to be shown in a table.
   */
  def configureTableViewer(table: TableViewer): Unit = {
    // TODO: Set up a content provider for the table.
    ???
  }

  /**
   * Invoked if the results are to be shown in a tree view
   */
  def configureTreeViewer(tree: TreeViewer): Unit = {
    // TODO: Set up a content provider for the tree view.
    ???
  }

}