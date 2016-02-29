package org.scala.tools.eclipse.search.ui

import org.eclipse.ui.part.ViewPart
import org.eclipse.swt.widgets.Composite
import org.eclipse.jface.viewers.TreeViewer
import org.eclipse.swt.widgets.Tree
import org.eclipse.swt.SWT
import org.eclipse.swt.layout.GridData
import org.eclipse.jface.viewers.ITreeContentProvider
import org.eclipse.jface.viewers.ILabelProvider
import org.eclipse.jface.action.Action
import org.eclipse.jface.action.IAction._
import CallHierarchyView._
import org.eclipse.jdt.internal.ui.JavaPluginImages
import org.eclipse.jface.action.Separator
import java.beans.PropertyChangeSupport
import org.eclipse.core.databinding.beans.BeanProperties
import org.eclipse.jface.databinding.viewers.ViewerSupport
import java.beans.PropertyChangeListener
import org.eclipse.swt.events.TreeListener
import org.eclipse.swt.events.TreeEvent
import org.eclipse.jface.databinding.viewers.ViewersObservables
import org.eclipse.jface.internal.databinding.viewers.SelectionChangedListener
import org.eclipse.jface.viewers.ISelectionChangedListener
import org.eclipse.jface.viewers.SelectionChangedEvent
import org.eclipse.jface.viewers.IStructuredSelection
import org.scala.tools.eclipse.search.searching.Location
import org.scala.tools.eclipse.search.Util
import org.eclipse.ui.part.FileEditorInput
import org.eclipse.ui.ide.IDE
import org.eclipse.jdt.internal.ui.JavaPlugin
import org.scalaide.ui.editor.InteractiveCompilationUnitEditor
import org.scala.tools.eclipse.search.Entity
import org.scala.tools.eclipse.search.searching.Finder
import org.scala.tools.eclipse.search.SearchPlugin
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.core.runtime.IProgressMonitor
import org.scala.tools.eclipse.search.searching.Scope
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.Status
import org.eclipse.ui.part.PageBook
import org.eclipse.swt.widgets.TreeColumn
import org.eclipse.core.databinding.beans.IBeanValueProperty
import org.eclipse.core.databinding.property.value.IValueProperty
import org.scalaide.core.IScalaProject
import org.scala.tools.eclipse.search.searching.Hit
import org.eclipse.swt.events.ControlListener
import org.eclipse.swt.events.ControlAdapter
import org.eclipse.swt.events.ControlEvent

class CallHierarchyView extends ViewPart {
  import CallHierarchyView._
  val input = RootNode()
  private var treeViewer: TreeViewer = _
  private val finder: Finder = SearchPlugin.finder
  override def createPartControl(parent: Composite) = {
    def createViewer(): TreeViewer = {
        
      class WidthManager(tc:TreeColumn, name:String) extends ControlAdapter{
        val cname = VIEW_ID+".TreeViewer.Columns.Width"+name
        SearchPlugin().getPreferenceStore.setDefault(cname, 200)
        tc.setWidth(SearchPlugin().getPreferenceStore.getInt(cname))
        tc.addControlListener(this)
        override def controlResized(e:ControlEvent)={
          SearchPlugin().getPreferenceStore.setValue(cname, math.max(tc.getWidth, 1))
        }
      }

      val tree = new Tree(parent, SWT.MULTI)
      tree.setHeaderVisible(true)
      val c1 = new TreeColumn(tree, SWT.LEFT)
      c1.setText("Caller")
      new WidthManager(c1,"c1")
      
      val c2 = new TreeColumn(tree, SWT.LEFT)
      c2.setText("Class")
      new WidthManager(c2,"c2")
      
      val c3 = new TreeColumn(tree, SWT.LEFT)
      c3.setText("Content")      
      new WidthManager(c3,"c3")
      
      val c4 = new TreeColumn(tree, SWT.LEFT)
      c4.setText("Project")
      new WidthManager(c4,"c4")
      
      val tv = new TreeViewer(tree)
      tv.getControl().setLayoutData(new GridData(GridData.FILL_BOTH))
      tv.setUseHashlookup(true)
      tv.setAutoExpandLevel(2)
      tv.getTree.addTreeListener(new TreeListener() {
        override def treeCollapsed(e: TreeEvent) {}

        override def treeExpanded(e: TreeEvent) {
          e.item.getData match {
            case n: CallerNode =>
              if (!n.list.isEmpty && n.list.head.isInstanceOf[QueryNode]) {
                n.list = List()
                val job = new Job("Searching for callers...") {
                  override def run(monitor: IProgressMonitor): IStatus = {
                    finder.findCallers(n.caller, n.scope, (offset, e, label, owner, project) => n.list = CallerNode(offset,  e, n.scope, label, owner, project) :: n.list, monitor)
                    Status.OK_STATUS
                  }
                }
                job.schedule()
              }
            case _ =>
          }
        }
      })
      tv.addSelectionChangedListener(new ISelectionChangedListener() {
        import org.scalaide.util.Utils.WithAsInstanceOfOpt
        override def selectionChanged(event: SelectionChangedEvent) = {
          val cn = treeViewer.getSelection.asInstanceOf[IStructuredSelection].getFirstElement
          cn match {
            case CallerNode(hit,  e, scope, label, owner, project) =>
              for {
                loc <- e.location
                file <- Util.getWorkspaceFile(loc.cu)
                val input = new FileEditorInput(file)
                desc <- Option(IDE.getEditorDescriptor(file.getName()))
                page <- Option(JavaPlugin.getActivePage)
                part <- Option(IDE.openEditor(page, input, desc.getId()))
                editor <- part.asInstanceOfOpt[InteractiveCompilationUnitEditor]
              } {
                editor.selectAndReveal(hit.offset, hit.word.length)
                setFocus()
              }

            case _ =>
          }
        }
      })
      tv
    }
    
    treeViewer = createViewer()
    fillViewMenu()
    fillActionBars()
    createDataBindings
  }

  private def createDataBindings = {
    val propNames: Array[IValueProperty] = BeanProperties.values(Array("label", "owner","line", "project")).map { x => x.asInstanceOf[IValueProperty] }
    ViewerSupport.bind(treeViewer, input,
      BeanProperties.list("list", classOf[Node]),
      propNames)
  }

  private def fillActionBars() = {
    val actionBars = getViewSite().getActionBars()
    val toolBar = actionBars.getToolBarManager()

  }

  override def setFocus() = {
    treeViewer.getTree.setFocus()
  }

  private def fillViewMenu() = {
    val actionBars = getViewSite().getActionBars()
    val viewMenu = actionBars.getMenuManager()
    viewMenu.add(new Separator())
  }

}

object CallHierarchyView {
  val VIEW_ID = "org.scala.tools.eclipse.search.ui.CallHierarchyView"
}


abstract class Node(label: String) {
  import scala.collection.JavaConversions._
  private val changeSupport = new PropertyChangeSupport(this)
  protected var list_ : List[Node] = List()

  def getLabel = label

  def getList: java.util.List[Node] = seqAsJavaList(list_)

  def list = list_
  def list_=(list: List[Node]) = changeSupport.firePropertyChange("list", seqAsJavaList(this.list), seqAsJavaList({ this.list_ = list; list }))
  def addPropertyChangeListener(listener: PropertyChangeListener) =
    changeSupport.addPropertyChangeListener(listener)

  def removePropertyChangeListener(listener: PropertyChangeListener) =
    changeSupport.removePropertyChangeListener(listener)

}

case class RootNode() extends Node("")
case class QueryNode() extends Node("Searching...") {
  override def list_=(list: List[Node]) = {}
}

case class CallerNode(hit:Hit,   caller: Entity,  scope: Scope, label: String, owner: Option[String], project: IScalaProject) extends Node(label) {
  def getOwner = owner.map(_.toString).getOrElse("")
  def getProject = project.underlying.getName
  def getLine = hit.lineContent.trim
  list_ = List(QueryNode())
}
