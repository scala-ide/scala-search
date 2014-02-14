package org.scala.tools.eclipse.search.ui

import org.scala.tools.eclipse.search.searching.Hit
import org.eclipse.jface.viewers.Viewer
import org.scalaide.logging.HasLogger
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

  // This returns the top-level elements in the tree-structure.
  override def getElements(inputElement: Object): Array[Object] = {
    input.projectNodes.toArray
  }

  override def getChildren(parentElement: Object): Array[Object] = {
    (parentElement match {
      case ProjectNode(_, _, files) => files
      case FileNode(_, _, lines) => lines
      case _ => Nil
    }).toArray.map(_.asInstanceOf[Object])

  }

  override def getParent(element: Object): Object = null

  override def hasChildren(element: Object): Boolean = element match {
    case ProjectNode(_, _, _) => true
    case FileNode(_, _, _) => true
    case _ => false
  }
}