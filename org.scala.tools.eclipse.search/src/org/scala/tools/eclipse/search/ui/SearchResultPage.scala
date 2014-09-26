package org.scala.tools.eclipse.search
package ui

import scala.reflect.runtime.universe
import org.scalaide.ui.internal.editor.ScalaSourceFileEditor
import org.scalaide.logging.HasLogger
import org.scalaide.util.internal.Utils.WithAsInstanceOfOpt

import org.eclipse.jdt.internal.ui.JavaPlugin
import org.eclipse.jface.viewers.IStructuredSelection
import org.eclipse.jface.viewers.OpenEvent
import org.eclipse.jface.viewers.StructuredViewer
import org.eclipse.jface.viewers.TableViewer
import org.eclipse.jface.viewers.TreeViewer
import org.eclipse.search.ui.text.AbstractTextSearchViewPage
import org.eclipse.ui.ide.IDE
import org.eclipse.ui.part.FileEditorInput
import org.scala.tools.eclipse.search.ErrorHandlingOption
import org.scala.tools.eclipse.search.Util
import org.scala.tools.eclipse.search.searching.Certain
import org.scala.tools.eclipse.search.searching.Hit
import org.scala.tools.eclipse.search.searching.Uncertain

/**
 * The page that is responsible for displaying the results of executing
 * a scala search query.
 */
class SearchResultPage
    extends AbstractTextSearchViewPage
    with HasLogger {

  // http://javasourcecode.org/html/open-source/eclipse/eclipse-3.5.2/org/eclipse/jdt/internal/ui/search/JavaSearchResultPage.java.html

  private val contentProvider = new ResultContentProvider(this)
  private val labelProvider = new ResultLabelProvider

  private val reporter = new DialogErrorReporter

  def view: StructuredViewer = {
    super.getViewer()
  }

  /**
   *  This method is called whenever all elements have been removed from the view.
   */
  def clear(): Unit = {}

  /**
   *  This method is called whenever the set of matches for the given elements changes.
   */
  def elementsChanged(elements: Array[Object]): Unit = {
    if (elements != null)
      contentProvider.elementsChanged(elements)
  }

  /**
   * Invoked if the results are to be shown in a table.
   */
  def configureTableViewer(table: TableViewer): Unit = {
    // TODO: Set up a content provider for the table.
    reporter.reportError("Table View is currently not supported")
  }

  /**
   * Invoked if the results are to be shown in a tree view
   */
  def configureTreeViewer(tree: TreeViewer): Unit = {
    tree.setContentProvider(contentProvider)
    tree.setLabelProvider(labelProvider)
  }

  override protected def handleOpen(event: OpenEvent): Unit = {

    def getElement(selection: IStructuredSelection): Option[Hit] = {
      val elem = selection.getFirstElement()
      elem match {
        case LineNode(Certain(x: Hit)) => Some(x)
        case LineNode(Uncertain(x: Hit)) => Some(x)
        case _ =>
          logger.debug(s"Unexpected selection type, got ${elem.getClass}")
          None
      }
    }

    (for {
      selection <- event.getSelection().asInstanceOfOpt[IStructuredSelection]
      hit       <- getElement(selection)
      page      <- Option(JavaPlugin.getActivePage) onEmpty reporter.reportError("Couldn't get active page")
      file      <- Util.getWorkspaceFile(hit.cu) onEmpty reporter.reportError("File no longer exists")
      val input = new FileEditorInput(file)
      desc      <- Option(IDE.getEditorDescriptor(file.getName()))
      part      <- Option(IDE.openEditor(page, input, desc.getId()))
      editor <- part.asInstanceOfOpt[ScalaSourceFileEditor]
    } yield {
      editor.selectAndReveal(hit.offset, hit.word.length)
    }) getOrElse {
      super.handleOpen(event)
    }
  }
}