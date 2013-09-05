package org.scala.tools.eclipse.search
package ui

import org.eclipse.swt.widgets.Composite
import org.eclipse.ui.part.ViewPart
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.layout.GridData
import org.eclipse.jface.viewers.TableViewer
import org.eclipse.swt.SWT
import org.eclipse.jface.viewers.TableViewerColumn
import org.eclipse.swt.widgets.Label
import org.eclipse.jface.viewers.TreeViewer
import org.eclipse.jface.viewers.Viewer
import org.eclipse.jface.viewers.StyledCellLabelProvider
import scala.tools.eclipse.logging.HasLogger
import org.eclipse.jface.viewers.IDoubleClickListener
import org.eclipse.jface.viewers.DoubleClickEvent
import org.eclipse.jface.viewers.IStructuredSelection
import org.scala.tools.eclipse.search.searching.EvaluatedNode
import scala.tools.eclipse.util.Utils._
import org.eclipse.jdt.internal.ui.JavaPlugin
import org.eclipse.ui.part.FileEditorInput
import org.eclipse.ui.ide.IDE
import scala.tools.eclipse.ScalaSourceFileEditor
import scala.tools.eclipse.ScalaProject
import org.scala.tools.eclipse.search.searching.Scope
import org.scala.tools.eclipse.search.searching.Certain

/**
 * This view presents a type hierarchy of a given type. The view consists of
 * three parts
 *
 *   - Supertypes: This is a table-view that displays all the direct
 *                 super-types of the given type.
 *   - Subtypes:   This is a table-view that displays all the direct
 *                 sub-types of the given type.
 *   - Inspector:  This is a table-view that displays all of the fields
 *                 of the type that is selected in the super-types or
 *                 sub-types table-view.
 */
class TypeHierarchyView extends ViewPart with HasLogger {

  private val finder = SearchPlugin.finder
  private val reporter: ErrorReporter = new DialogErrorReporter

  var superclasses: TreeViewer = _
  var subclasses: TreeViewer = _
  var superLabel: Label = _
  var subLabel: Label = _

  def setRoot(entity: TypeEntity, scope: Scope): Unit = {
    subclasses.setInput((entity, scope))
    superclasses.setInput((entity, scope))
    superLabel.setText(s"Super-classes of '${entity.displayName}'")
    subLabel.setText(s"Sub-classes of '${entity.displayName}'")
  }

  def clear(): Unit = {
    subclasses.setInput(null)
    superclasses.setInput(null)
  }

  override def createPartControl(parent: Composite): Unit = {
    // Layout
    val layout = new GridLayout()
    layout.numColumns = 1
    parent.setLayout(layout)
    val layoutData = new GridData()
    layoutData.horizontalAlignment = GridData.FILL
    layoutData.verticalAlignment = GridData.FILL
    parent.setLayoutData(layoutData)

    // Configure the Super-types view
    superLabel = new Label(parent, SWT.BORDER)
    superLabel.setText("Super-classes")
    superLabel.setLayoutData(new GridData(GridData.GRAB_HORIZONTAL | GridData.HORIZONTAL_ALIGN_FILL))

    superclasses = new TreeViewer(parent, SWT.VIRTUAL| SWT.BORDER)
    configure(superclasses)
    setupEventHandler(superclasses)

    superclasses.setContentProvider(
        new TypeHierarchyTreeContentProvider(superclasses, (e, _, _, h) => e.supertypes map Certain.apply foreach h))
    superclasses.setLabelProvider(new TypeHierarchyTreeLabelProvider(
        leafLabel = "No Super-types"
    ))

    // Configure the Sub-types view
    subLabel = new Label(parent, SWT.NONE)
    subLabel.setText("Sub-classes")
    subLabel.setLayoutData(new GridData(GridData.GRAB_HORIZONTAL | GridData.HORIZONTAL_ALIGN_FILL))

    subclasses = new TreeViewer(parent, SWT.VIRTUAL| SWT.BORDER)
    configure(subclasses)
    setupEventHandler(subclasses)

    subclasses.setContentProvider(
        new TypeHierarchyTreeContentProvider(subclasses, (e, s, m, h) => finder.findSubtypes(e, s, m)(h)))
    subclasses.setLabelProvider(new TypeHierarchyTreeLabelProvider(
        leafLabel = "No Sub-types"
    ))

    // disabled as displaying of members isn't yet implemented
    // addInspectorView(parent)
  }

  private def addInspectorView(parent: Composite): Unit = {
    val inspectLabel = new Label(parent, SWT.NONE)
    inspectLabel.setText("Inspector")
    inspectLabel.setLayoutData(new GridData(GridData.GRAB_HORIZONTAL | GridData.HORIZONTAL_ALIGN_FILL))

    val inspector = new TableViewer(parent,  SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.BORDER)
    inspector.getControl().setEnabled(false)
    configure(inspector)
  }

  private def configure(viewer: Viewer): Unit = {
    val data = new GridData()
    data.heightHint = (viewer.getControl.getParent().getSize().y / 3) // LOL! Uses y to represent the height of the view.
    data.verticalAlignment = GridData.FILL
    data.horizontalAlignment = GridData.FILL
    data.grabExcessHorizontalSpace = true
    data.grabExcessVerticalSpace = true
    viewer.getControl.setLayoutData(data)
  }

  private def setupEventHandler(viewer: TreeViewer): Unit = {
    viewer.addDoubleClickListener(new IDoubleClickListener() {
      override def doubleClick(event: DoubleClickEvent): Unit = {

        (for {
          selection <- event.getSelection().asInstanceOfOpt[IStructuredSelection]
          node      <- selection.getFirstElement().asInstanceOfOpt[EvaluatedNode] onEmpty logger.debug("Unexpected selection type")
          page      <- Option(JavaPlugin.getActivePage) onEmpty reporter.reportError("Couldn't get active page")
          location  <- node.elem.value.location onEmpty reporter.reportError("The type isn't defined in any sources we have access to")
          file      <- Util.getWorkspaceFile(location.cu) onEmpty reporter.reportError("File no longer exists")
          val input = new FileEditorInput(file)
          desc      <- Option(IDE.getEditorDescriptor(file.getName()))
          part      <- Option(IDE.openEditor(page, input, desc.getId()))
          editor <- part.asInstanceOfOpt[ScalaSourceFileEditor]
        } {
          editor.selectAndReveal(location.offset, node.elem.value.name.length)
        })

      }
    })
  }

  override def setFocus(): Unit = {}

}

object TypeHierarchyView {

  // same as in plugin.xml. Don't know if it has to be.
  final val VIEW_ID = "org.scala.tools.eclipse.search.ui.typehiearchy"

}