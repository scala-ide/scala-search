package org.scala.tools.eclipse.search.indexing

import scala.tools.eclipse.javaelements.ScalaSourceFile
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field

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

  override def toString = "%s in %s at char %s %s".format(
      word,
      file.getPath().makeAbsolute().toOSString(),
      offset.toString,
      occurrenceKind.toString)

  /**
   * Create a Lucene document based on the information stored in the
   * occurrence.
   */
  def toDocument: Document = {
    val doc = new Document
    doc.add(new Field("word", word, Field.Store.YES, Field.Index.NOT_ANALYZED))
    doc.add(new Field("file", file.getPath().makeAbsolute().toOSString(), Field.Store.YES, Field.Index.NOT_ANALYZED))
    doc.add(new Field("offset", offset.toString, Field.Store.YES, Field.Index.NOT_ANALYZED))
    doc.add(new Field("occurrenceKind", occurrenceKind.toString, Field.Store.YES, Field.Index.NOT_ANALYZED))
    doc.add(new Field("entityKind", entity.toString, Field.Store.YES, Field.Index.NOT_ANALYZED))
    doc
  }

}

object Occurrence {

  def fromDocument(doc: Document): Occurrence = ???

}