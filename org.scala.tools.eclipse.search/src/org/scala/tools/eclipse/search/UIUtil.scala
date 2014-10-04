package org.scala.tools.eclipse.search

import org.eclipse.jface.text.ITextSelection
import org.eclipse.ui.texteditor.ITextEditor

object UIUtil {
  def getSelection(editor: ITextEditor): Option[ITextSelection] = {
    editor.getSelectionProvider().getSelection() match {
      case sel: ITextSelection => Some(sel)
      case _ => None
    }
  }
}