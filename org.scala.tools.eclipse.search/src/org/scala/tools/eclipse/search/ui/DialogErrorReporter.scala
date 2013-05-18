package org.scala.tools.eclipse.search.ui

import org.scala.tools.eclipse.search.ErrorReporter
import org.eclipse.jface.dialogs.MessageDialog
import org.eclipse.ui.PlatformUI

/**
 * Uses Eclipse MessageDialog to report errors
 */
trait DialogErrorReporter extends ErrorReporter {

  def reportError(msg: String): Unit = {
    for {
      wb <- Option(PlatformUI.getWorkbench())
      window <- Option(wb.getActiveWorkbenchWindow())
    } {
      MessageDialog.openError( window.getShell(), "Scala Semantic Search", msg)
    }
  }

}