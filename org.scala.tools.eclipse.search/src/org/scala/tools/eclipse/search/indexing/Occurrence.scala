package org.scala.tools.eclipse.search.indexing

import scala.tools.eclipse.javaelements.ScalaSourceFile
import org.scala.tools.eclipse.search.searching.Hit
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import org.scala.tools.eclipse.search.searching.ExactHit
import org.scala.tools.eclipse.search.searching.PotentialHit

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
  val WORD                 = "word"
  val PATH                 = "path"
  val OFFSET               = "offset"
  val OCCURRENCE_KIND      = "occurrenceKind"
  val PROJECT_NAME         = "project"
  val LINE_CONTENT         = "lineContent"
  val IS_IN_SUPER_POSITION = "isInSuperPosition"
}

/**
 * Represents an occurrence of a word that we're interested in when
 * indexing the parse trees.
 */
case class Occurrence(
    word: String,
    file: ScalaCompilationUnit,
    offset: Int, // char offset from beginning of file
    occurrenceKind: OccurrenceKind,
    lineContent: String = "",
    isInSuperPosition: Boolean = false) {

  override def equals(other: Any) = other match {
    // Don't compare lineCOntent
    case o: Occurrence =>
      word == o.word &&
      file == o.file &&
      offset == o.offset &&
      occurrenceKind == o.occurrenceKind &&
      isInSuperPosition == o.isInSuperPosition
    case _ => false
  }

  override def toString = "%s in %s at char %s %s".format(
      word,
      file.file.file.getAbsolutePath(),
      offset.toString,
      occurrenceKind.toString)

  def toPotentialHit = PotentialHit(file, word, lineContent, offset)

  def toExactHit = ExactHit(file, word, lineContent, offset)

}