package org.scala.tools.eclipse.search.indexing

import scala.tools.eclipse.javaelements.ScalaSourceFile
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import scala.tools.eclipse.logging.HasLogger
import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.runtime.Path

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

sealed abstract class EntityKind
object EntityKind {
  def fromString(str: String): EntityKind = str match {
    case "Method" => Method
    case x => throw new Exception("Unknown entity kind " + x)
  }
}

case object Method extends EntityKind

object LuceneFields {

  val WORD = "word"
  val FILE = "file"
  val OFFSET = "offset"
  val OCCURRENCE_KIND = "occurrenceKind"
  val ENTITY_KIND = "entityKind"

}

/**
 * Represents an occurrence of a word that we're interested in when
 * indexing the parse trees.
 */
case class Occurrence(
    word: String,
    file: ScalaSourceFile,
    offset: Int,                     /* char offset from beginning of file */
    occurrenceKind: OccurrenceKind,
    entity: EntityKind) extends HasLogger {            /* None if we can't tell from the parse tree */

  override def toString = "%s in %s at char %s %s".format(
      word,
      file.file.file.getAbsolutePath(),
      offset.toString,
      occurrenceKind.toString)

  /**
   * Create a Lucene document based on the information stored in the
   * occurrence.
   */
  def toDocument: Document = {
    import LuceneFields._
    val doc = new Document
    doc.add(new Field(WORD, word, Field.Store.YES, Field.Index.NOT_ANALYZED))
    doc.add(new Field(FILE, file.file.file.getAbsolutePath(), Field.Store.YES, Field.Index.NOT_ANALYZED))
    doc.add(new Field(OFFSET, offset.toString, Field.Store.YES, Field.Index.NOT_ANALYZED))
    doc.add(new Field(OCCURRENCE_KIND, occurrenceKind.toString, Field.Store.YES, Field.Index.NOT_ANALYZED))
    doc.add(new Field(ENTITY_KIND, entity.toString, Field.Store.YES, Field.Index.NOT_ANALYZED))
    doc
  }

}

object Occurrence {

  /**
   * Converts a lucene Document into an Occurrence. Will throw exception if
   * there are things that can't be converted to the expected type.
   */
  def fromDocument(doc: Document): Occurrence = {
    import LuceneFields._
    (for {
      word           <- Option(doc.get(WORD))
      path           <- Option(doc.get(FILE))
      offset         <- Option(doc.get(OFFSET))
      occurrenceKind <- Option(doc.get(OCCURRENCE_KIND))
      entityKind     <- Option(doc.get(ENTITY_KIND))
    } yield {
      // We have the absolute path but need 
      val workspace = ResourcesPlugin.getWorkspace()
      val location= Path.fromOSString(path)
      val ifile = workspace.getRoot().getFileForLocation(location)

      val file = ScalaSourceFile.createFromPath(ifile.getFullPath().toOSString()).getOrElse {
        throw new Exception("Wasn't able to create ScalaSourceFile from path " + path)
      }
      Occurrence(word, file, Integer.parseInt(offset), OccurrenceKind.fromString(occurrenceKind), EntityKind.fromString(entityKind))
    }) getOrElse {
      throw new Exception("Wasn't able to convert document to occurrence")
    }
  }

}