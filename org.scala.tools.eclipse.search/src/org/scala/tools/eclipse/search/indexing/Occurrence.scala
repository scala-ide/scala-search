package org.scala.tools.eclipse.search.indexing

import scala.tools.eclipse.javaelements.ScalaSourceFile

sealed abstract class OccurrenceKind
case object Declaration extends OccurrenceKind
case object Reference extends OccurrenceKind

sealed abstract class EntityKind
case object Method extends EntityKind

/**
 * Represents an occurrence of a word that we're interested in when
 * indexing the parse trees.
 */
case class Occurrence(
    word: String,
    file: ScalaSourceFile,
    offset: Int,                     /* char offset from beginning of file */
    occurrenceKind: OccurrenceKind,
    entity: EntityKind) {            /* None if we can't tell from the parse tree */

  override def toString = "%s in %s at char %s %s".format(word, file.file.file.getName(), offset.toString, occurrenceKind.toString) 

}