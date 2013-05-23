package org.scala.tools.eclipse.search.ui

import org.scala.tools.eclipse.search.searching.Result
import org.eclipse.jface.viewers.Viewer
import scala.tools.eclipse.logging.HasLogger
import org.eclipse.jface.viewers.ITreeContentProvider
import org.eclipse.jface.viewers.AbstractTreeViewer

/**
 * Responsible for telling Eclipse what content to show after a successful
 * search.
 */
class ResultContentProvider(page: SearchResultPage) extends ITreeContentProvider with HasLogger {

  private var input: SearchResult = _

  def elementsChanged(elements: Array[Object]): Unit = {
    // Performance-wise this can be imporoved ALOT!
    // see http://javasourcecode.org/html/open-source/eclipse/eclipse-3.5.2/org/eclipse/jdt/internal/ui/search/LevelTreeContentProvider.java.html
    val viewer = page.view.asInstanceOf[AbstractTreeViewer]
    viewer.refresh()
  }

  override def dispose() {
    input = null
  }

  override def inputChanged(viewer: Viewer, oldInput: Object, newInput: Object) {
    if (newInput != null) {
      if (newInput.isInstanceOf[SearchResult]) {
        input = newInput.asInstanceOf[SearchResult]
      } else {
        logger.debug(s"Expeced SearchResult but got ${newInput}")
      }
    }
  }

  override def getElements(inputElement: Object): Array[Object] = {
    // This returns the top-level elements, i.e. the filename and the match
    // count in that file.
    input.resultsGroupedByFile.map { case (str, seq) => (str, seq.size) }.toArray
  }

  override def getChildren(parentElement: Object): Array[Object] = {
    parentElement match {
      case (x: String, _) =>
        input.resultsGroupedByFile.get(x).map(_.toArray.map(_.asInstanceOf[Object])).getOrElse(Array[Object]())
      case _ => Array[Object]()
    }
  }

  override def getParent(element: Object): Object = null

  override def hasChildren(element: Object): Boolean = element match {
    case (x: String, _) => input.resultsGroupedByFile.get(x).fold(false)(_ => true)
    case _ => false
  }
}