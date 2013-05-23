package org.scala.tools.eclipse.search.ui

import org.eclipse.jface.viewers.TableViewer
import org.eclipse.jface.viewers.TreeViewer
import org.eclipse.search.ui.text.AbstractTextSearchViewPage
import org.eclipse.jface.viewers.IContentProvider
import org.eclipse.jface.viewers.ILabelProvider
import org.scala.tools.eclipse.search.searching.Result
import scala.tools.eclipse.InteractiveCompilationUnit
import org.eclipse.jface.viewers.Viewer
import scala.tools.eclipse.logging.HasLogger
import org.eclipse.jface.viewers.ITreeContentProvider
import org.eclipse.jface.viewers.StructuredViewer
import org.eclipse.jface.viewers.IStructuredSelection
import org.eclipse.jface.viewers.OpenEvent
import org.eclipse.jface.action.Action
import org.eclipse.ui.IWorkbenchWindowActionDelegate
import org.eclipse.ui.IWorkbenchWindow
import org.eclipse.jdt.internal.ui.JavaPlugin
import org.eclipse.ui.part.FileEditorInput
import org.eclipse.ui.ide.IDE
import org.eclipse.ui.ide.IDE
import scala.tools.eclipse.util.Utils.any2optionable
import scala.tools.eclipse.ScalaSourceFileEditor

/**
 * The page that is responsible for displaying the results of executing
 * a scala search query.
 */
class SearchResultPage
    extends AbstractTextSearchViewPage
    with HasLogger
    with DialogErrorReporter {

  // http://javasourcecode.org/html/open-source/eclipse/eclipse-3.5.2/org/eclipse/jdt/internal/ui/search/JavaSearchResultPage.java.html

  val contentProvider = new ResultContentProvider(this)
  val labelProvider = new ResultLabelProvider

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
    reportError("Table View is currently not supported")
  }

  /**
   * Invoked if the results are to be shown in a tree view
   */
  def configureTreeViewer(tree: TreeViewer): Unit = {
    tree.setContentProvider(contentProvider)
    tree.setLabelProvider(labelProvider)
  }

  override protected def handleOpen(event: OpenEvent): Unit = {
    val firstElement = event.getSelection().asInstanceOf[IStructuredSelection].getFirstElement()
    if (firstElement.isInstanceOf[Result]) {
      val result = firstElement.asInstanceOf[Result]
      val page = JavaPlugin.getActivePage()
      val file = result.cu.workspaceFile
      val input = new FileEditorInput(file)
      val desc = IDE.getEditorDescriptor(file.getName())
      val part = IDE.openEditor(page, input, desc.getId())
      val editor = part.asInstanceOfOpt[ScalaSourceFileEditor].get
      editor.selectAndReveal(result.offset, result.word.length)
    } else {
      super.handleOpen(event)
    }
  }
}