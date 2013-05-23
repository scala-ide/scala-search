package org.scala.tools.eclipse.search

import scala.tools.eclipse.ScalaSourceFileEditor
import org.eclipse.jface.text.ITextSelection
import org.eclipse.ui.IEditorPart

object UIUtil {
  def getSelection(editor: ScalaSourceFileEditor): Option[ITextSelection] = {
    editor.getSelectionProvider().getSelection() match {
      case sel: ITextSelection => Some(sel)
      case _ => None
    }
  }
}