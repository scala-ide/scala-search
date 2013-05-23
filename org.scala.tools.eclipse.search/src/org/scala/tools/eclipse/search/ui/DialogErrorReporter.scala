package org.scala.tools.eclipse.search
package ui

import org.scala.tools.eclipse.search.ErrorReporter
import org.eclipse.jface.dialogs.MessageDialog
import org.eclipse.ui.PlatformUI
import scala.tools.eclipse.logging.HasLogger

/**
 * Uses Eclipse MessageDialog to report errors
 */
trait DialogErrorReporter extends ErrorReporter with HasLogger {

  def reportError(msg: String): Unit = {
    for {
      wb     <- Option(PlatformUI.getWorkbench()) onEmpty logger.debug("Couldn'get workbench")
      window <- Option(wb.getActiveWorkbenchWindow()) onEmpty logger.debug("Couldn'get window")
    } {
      MessageDialog.openError( window.getShell(), "Scala Semantic Search", msg)
    }
  }

}