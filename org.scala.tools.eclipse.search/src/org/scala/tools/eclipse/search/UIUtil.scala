package org.scala.tools.eclipse.search

import org.scalaide.ui.internal.editor.ScalaSourceFileEditor
import org.eclipse.jface.text.ITextSelection

object UIUtil {
  def getSelection(editor: ScalaSourceFileEditor): Option[ITextSelection] = {
    editor.getSelectionProvider().getSelection() match {
      case sel: ITextSelection => Some(sel)
      case _ => None
    }
  }
}