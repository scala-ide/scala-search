package org.scala.tools.eclipse.search.ui

import org.eclipse.jface.viewers.LabelProvider
import org.eclipse.jface.viewers.ViewerCell
import org.eclipse.jface.viewers.StyledString
import org.eclipse.jface.viewers.StyledCellLabelProvider
import org.eclipse.ui.PlatformUI
import org.eclipse.ui.ISharedImages
import org.eclipse.jface.resource.JFaceResources
import org.eclipse.swt.graphics.RGB
import scala.tools.eclipse.logging.HasLogger
import org.scala.tools.eclipse.search.searching.Hit
import org.eclipse.jdt.internal.ui.JavaPlugin
import org.eclipse.ui.part.FileEditorInput
import org.eclipse.ui.ide.IDE
import org.scala.tools.eclipse.search.UIUtil
import scala.tools.eclipse.ScalaImages
import org.scala.tools.eclipse.search.searching.Certain
import org.scala.tools.eclipse.search.searching.Uncertain

/**
 * Responsible for telling Eclipse how to render the results in the
 * tree view (i.e. the view that shows the results).
 */
class ResultLabelProvider extends StyledCellLabelProvider with HasLogger {

  private final val HIGHLIGHT_COLOR_NAME = "org.scala.tools.eclipse.search"
  JFaceResources.getColorRegistry().put(HIGHLIGHT_COLOR_NAME, new RGB(206, 204, 247));

  override def update(cell: ViewerCell) {

    val text = new StyledString

    cell.getElement() match {
      case (str: String, count: Int) =>
        text.append(str)
        text.append(" (%s)".format(count), StyledString.COUNTER_STYLER)
        cell.setImage(ScalaImages.SCALA_FILE.createImage())
      case Certain(Hit(_,_,line, _)) =>
        val styled = new StyledString(line.trim)
        text.append(styled)
      case Uncertain(Hit(_,_,line,_)) =>
        val styled = new StyledString(s"(?) ${line.trim}")
        text.append(styled)
      case x =>
        logger.debug(s"Expected content of either a tuple or Confidence[Hit], got: ${x}")
    }

    cell.setText(text.toString)
    cell.setStyleRanges(text.getStyleRanges)
    super.update(cell)
  }

}