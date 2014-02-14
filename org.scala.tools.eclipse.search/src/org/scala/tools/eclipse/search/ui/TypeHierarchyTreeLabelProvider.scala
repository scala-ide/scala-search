package org.scala.tools.eclipse.search
package ui

import org.eclipse.jface.viewers.StyledCellLabelProvider
import org.eclipse.jface.viewers.ViewerCell
import org.eclipse.jface.viewers.StyledString
import org.scalaide.logging.HasLogger
import org.scala.tools.eclipse.search.searching.Certain
import org.scala.tools.eclipse.search.searching.Uncertain
import org.scalaide.ui.internal.ScalaImages
import org.scala.tools.eclipse.search.searching.LeafNode
import org.scala.tools.eclipse.search.searching.EvaluatingNode
import org.scala.tools.eclipse.search.searching.EvaluatedNode
import org.eclipse.jface.viewers.StyledString
import org.eclipse.jdt.internal.ui.JavaPluginImages
import org.eclipse.jdt.ui.JavaUI
import org.eclipse.jdt.ui.JavaElementImageDescriptor
import org.eclipse.jface.viewers.DecorationOverlayIcon
import org.eclipse.jface.viewers.IDecoration

/**
 * Used by the TypeHierarchyView to produce the labels that are shown in the super- and
 * sub-type tree viewers.
 * 
 * The type-hierarchy is represented using `TypeHierarchyNode` and they're produced in
 * `TypeHierarchyTreeContentProvider`.
 */
class TypeHierarchyTreeLabelProvider(leafLabel: String) extends StyledCellLabelProvider with HasLogger {

  // Drawing code for the label.
  override def update(cell: ViewerCell) {
    val text = new StyledString

    cell.getElement() match {
      case LeafNode => text.append(leafLabel)
      case EvaluatingNode => text.append("Loading...")
      case EvaluatedNode(Certain(entity)) => setEntity(cell, text, entity)
      case EvaluatedNode(Uncertain(entity)) => {
        setEntity(cell, text, entity)
        text.append(" - Potential match", StyledString.QUALIFIER_STYLER)
      }
      case x => text.append(x.toString)
    }

    cell.setText(text.toString)
    cell.setStyleRanges(text.getStyleRanges)
    super.update(cell)
  }

  private def setEntity(cell: ViewerCell, text: StyledString, entity: TypeEntity): Unit = {
    entity match {
      case Type(_, name) =>
        text.append(name)
        cell.setImage(ScalaImages.SCALA_TYPE.createImage)
      case Trait(_, name) =>
        text.append(name)
        cell.setImage(ScalaImages.SCALA_TRAIT.createImage)
      case c @ Class(_, name) if c.isAbstract =>
        text.append(name)
        val base = ScalaImages.SCALA_CLASS
        val overlay = JavaPluginImages.DESC_OVR_ABSTRACT_CLASS
        val imageDesc = new DecorationOverlayIcon(ScalaImages.SCALA_CLASS.createImage, overlay, IDecoration.TOP_LEFT)
        cell.setImage(imageDesc.createImage)
      case Class(_, name) =>
        text.append(name)
        cell.setImage(ScalaImages.SCALA_CLASS.createImage)
      case Module(_, name) =>
        text.append(name)
        cell.setImage(ScalaImages.SCALA_OBJECT.createImage)
      case Interface(_, name) =>
        text.append(name)
        cell.setImage(JavaUI.getSharedImages.getImage(JavaPluginImages.IMG_OBJS_INTERFACE))
    }
    if (entity.location.isEmpty) {
      text.append(" - Not defined in project", StyledString.QUALIFIER_STYLER)
    }

  }

}