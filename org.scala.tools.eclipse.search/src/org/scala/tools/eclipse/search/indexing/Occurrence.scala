package org.scala.tools.eclipse.search.indexing

import org.scala.tools.eclipse.search.searching.Hit
import org.scala.tools.eclipse.search.searching.Hit
import org.scala.tools.eclipse.search.Util
import org.scalaide.core.compiler.InteractiveCompilationUnit

/**
 * Represents the various kinds of occurrences that we deal with
 */
sealed abstract class OccurrenceKind
object OccurrenceKind {
  def fromString(str: String): OccurrenceKind = str match {
    case "Declaration" => Declaration
    case "Reference" => Reference
    case x => throw new Exception("Unknown occurrence kind " + x)
  }
}
case object Declaration extends OccurrenceKind
case object Reference extends OccurrenceKind

object LuceneFields {
  val WORD = "word"
  val PATH = "path"
  val OFFSET = "offset"
  val OCCURRENCE_KIND = "occurrenceKind"
  val PROJECT_NAME = "project"
  val LINE_CONTENT = "lineContent"
  val IS_IN_SUPER_POSITION = "isInSuperPosition"
  val CALLER_OFFSET = "callerOffset"
}

/**
 * Represents an occurrence of a word that we're interested in when
 * indexing the parse trees.
 */
case class Occurrence(
    word: String,
    file: InteractiveCompilationUnit,
    offset: Int, // char offset from beginning of file
    occurrenceKind: OccurrenceKind,
    lineContent: String = "",
    isInSuperPosition: Boolean = false,
    callerOffset: Option[Int] = None) {

  override def equals(other: Any) = other match {
    // Don't compare lineCOntent
    case o: Occurrence =>
      word == o.word &&
        file == o.file &&
        offset == o.offset &&
        occurrenceKind == o.occurrenceKind &&
        isInSuperPosition == o.isInSuperPosition &&
        callerOffset == o.callerOffset
    case _ => false
  }

  override def toString = "%s in %s at char %s %s".format(
    word,
    Util.getWorkspaceFile(file).map(_.getProjectRelativePath().toString()).getOrElse("deleted file"),
    offset.toString,
    occurrenceKind.toString)

  def toHit = Hit(file, word, lineContent, offset, callerOffset)

}