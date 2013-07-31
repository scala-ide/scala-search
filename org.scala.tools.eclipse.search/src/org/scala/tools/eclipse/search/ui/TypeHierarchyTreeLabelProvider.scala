package org.scala.tools.eclipse.search
package ui

import org.eclipse.jface.viewers.StyledCellLabelProvider
import org.eclipse.jface.viewers.ViewerCell
import org.eclipse.jface.viewers.StyledString
import scala.tools.eclipse.logging.HasLogger
import org.scala.tools.eclipse.search.searching.Certain
import org.scala.tools.eclipse.search.searching.Uncertain
import scala.tools.eclipse.ScalaImages
import org.scala.tools.eclipse.search.searching.LeafNode
import org.scala.tools.eclipse.search.searching.EvaluatingNode
import org.scala.tools.eclipse.search.searching.EvaluatedNode

/**
 * Used by the TypeHierarchyView to produce the labels that are shown in the super- and
 * sub-type tree viewers.
 * 
 * The type-hierarchy is represented using `TypeHierarchyNode` and they're produced in
 * `TypeHierarchyTreeContentProvider`.
 */
class TypeHierarchyTreeLabelProvider extends StyledCellLabelProvider with HasLogger {

  // Drawing code for the label.
  override def update(cell: ViewerCell) {
    val text = new StyledString

    cell.getElement() match {
      case LeafNode => text.append("No subtypes")
      case EvaluatingNode => text.append("Loading...")
      case EvaluatedNode(Certain(entity)) => setEntity(cell, text, entity)
      case EvaluatedNode(Uncertain(entity)) =>
        text.append("(?) ")
        setEntity(cell, text, entity)
      case x => text.append(x.toString)
    }

    cell.setText(text.toString)
    cell.setStyleRanges(text.getStyleRanges)
    super.update(cell)
  }

  private def setEntity(cell: ViewerCell, text: StyledString, entity: TypeEntity): Unit = {
    entity match {
      case Type(name, _) =>
        text.append(name)
        cell.setImage(ScalaImages.SCALA_TYPE.createImage)
      case Trait(name, _) =>
        text.append(name)
        cell.setImage(ScalaImages.SCALA_TRAIT.createImage)
      case Class(name, _) =>
        text.append(name)
        cell.setImage(ScalaImages.SCALA_CLASS.createImage)
      case Module(name, _) =>
        text.append(name)
        cell.setImage(ScalaImages.SCALA_OBJECT.createImage)
    }
  }

}