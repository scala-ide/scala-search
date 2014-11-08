package org.scala.tools.eclipse.search
package searching

/**
 * Tree structure that represents a lazy type-hierarchy.
 *
 * - EvaluatedNode: Is a node in the hierarchy that has already been
 *   evaluated so know the sub-types of the given node.
 * - EvaluatingNode: Is used when it is finding the sub-types of a
 *   of the node. This is used to show a small text to the user
 *   while we look for sub-types
 * - LeafNode is used to represent the bottom of the type-hierarchy
 */
sealed abstract class TypeHierarchyNode
case class EvaluatedNode(elem: Confidence[TypeEntity]) extends TypeHierarchyNode
case object EvaluatingNode extends TypeHierarchyNode
case object LeafNode extends TypeHierarchyNode
