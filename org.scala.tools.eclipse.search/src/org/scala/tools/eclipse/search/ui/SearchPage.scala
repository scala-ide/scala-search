package org.scala.tools.eclipse.search.ui

import org.eclipse.search.ui.ISearchPage
import org.eclipse.jface.dialogs.DialogPage
import org.eclipse.swt.widgets.Composite
import org.eclipse.search.ui.ISearchPageContainer
import org.eclipse.swt.SWT
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.Label
import org.scalaide.logging.HasLogger
import org.eclipse.swt.layout.GridData
import org.eclipse.jface.dialogs.Dialog
import org.eclipse.swt.widgets.Control
import org.eclipse.swt.widgets.Group
import org.eclipse.swt.widgets.Button
import org.eclipse.swt.events.SelectionAdapter
import org.eclipse.swt.events.SelectionEvent
import org.eclipse.swt.widgets.Text
import org.eclipse.swt.events.ModifyListener
import org.eclipse.swt.events.ModifyEvent

/**
 * This is the page that is rendered in the Eclipse search dialog. It is 
 * Hooked into Eclipse through the SearchPage extension point.
 */
class SearchPage extends DialogPage with ISearchPage with HasLogger {

  /**
   * This is invoked when the user presses the search button.
   */
  override def performAction(): Boolean = {
    // TODO: Read input and perform a background query
    true
  }

  /**
   * Called when the the page it put inside the container. We need to keep
   * a reference to this in our page so we can read the scope options the
   * user has selected.
   */
  override def setContainer(container: ISearchPageContainer): Unit = {}

  /**
   * Called by Eclipse. It is responsible for creating all the UI elements.
   */
  override def createControl(parent: Composite): Unit = {

    val page = new Composite(parent, SWT.FILL)
    val label = new Label(page, SWT.LEFT)
    val layout = new GridLayout(2, true)
    layout.horizontalSpacing = 10
    page.setLayout(layout)

    label.setText("Nothing fancy to see here yet.")
    label.setLayoutData(new GridData(GridData.FILL, GridData.FILL, false, false, 2, 1))

    // If we don't call this we get an NPE in Eclipse w/o this class in the stacktrace. Lovely
    setControl(page)
    Dialog.applyDialogFont(page)
  }

}