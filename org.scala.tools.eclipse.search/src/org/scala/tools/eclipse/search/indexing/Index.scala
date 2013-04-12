package org.scala.tools.eclipse.search.indexing

import java.io.File
import java.io.IOException

import scala.Array.fallbackCanBuildFrom
import scala.collection.JavaConverters.asJavaIterableConverter
import scala.tools.eclipse.logging.HasLogger
import scala.util.Try
import scala.util.control.{Exception => Ex}
import scala.util.control.Exception.Catch

import org.apache.lucene.analysis.core.SimpleAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.Term
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TermQuery
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.util.Version
import org.eclipse.core.resources.IProject
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.Path
import org.scala.tools.eclipse.search.SearchPlugin
import org.scala.tools.eclipse.search.Util
import org.scala.tools.eclipse.search.using

import LuceneFields.OCCURRENCE_KIND
import LuceneFields.OFFSET
import LuceneFields.PATH
import LuceneFields.PROJECT_NAME
import LuceneFields.WORD

/**
 * A Lucene based index of all occurrences of Scala entities recorded in the workspace.
 *
 * All of the methods published by Index have a return type of Try meaning that they all
 * might fail in one way or the other. It is the responsibility of the user of the Index
 * to react to these failures in a meaningful way.
 *
 * See `toDocument` for more information about what is stored in the index.
 *
 * A separate Lucene index is stored on disk for each project in the Workspace.
 *
 * This class assumes that the resources passed to the different methods exist and, in
 * the case of a IProject, it's open.
 *
 * This class is thread-safe.
 */
class Index(indicesRoot: File) extends HasLogger {

  // internal errors, users shouldn't worry about these
  protected trait ConversionError
  protected case class MissingFile(path: String, project: String) extends ConversionError
  protected case class InvalidDocument(doc: Document) extends ConversionError

  protected def config = {
    val analyzer = new SimpleAnalyzer(Version.LUCENE_41)
    new IndexWriterConfig(Version.LUCENE_41, analyzer)
  }

  //  TODO: https://scala-ide-portfolio.assembla.com/spaces/scala-ide/tickets/1001661-make-max-number-of-matches-configurable
  protected val MAX_POTENTIAL_MATCHES = 100000

  /**
   * Tries to add all occurrences found in a given file to the index. This method can
   * fail with
   *
   * IOException - If there is an underlying IOException when trying to open
   *               the directory on disk where the Lucene index is persisted.
   *
   * CorruptIndexException - If the Index somehow has become corrupted.
   *
   */
  def addOccurrences(occurrences: Seq[Occurrence], project: IProject): Try[Unit] = {
    doWithWriter(project) { writer =>
      val docs = occurrences.map( toDocument(project, _) )
      writer.addDocuments(docs.toIterable.asJava)
    }
  }

  /**
   * Tries to remove all occurrences recorded in a given file, identified by the project
   * relative path and the name of the project in the workspace. This method can fail
   * with
   *
   * IOException - If there is an underlying IOException when trying to open
   *               the directory on disk where the Lucene index is persisted.
   *
   * CorruptIndexException - If the Index somehow has become corrupted.
   */
  def removeOccurrencesFromFile(path: IPath, project: IProject): Try[Unit] = {
    doWithWriter(project) { writer =>
      val query = new BooleanQuery()
      query.add(new TermQuery(Terms.pathTerm(path)), BooleanClause.Occur.MUST)
      query.add(new TermQuery(Terms.projectTerm(project)), BooleanClause.Occur.MUST)
      writer.deleteDocuments(query)
    }
  }

  /**
   * ARM method for writing to the index. Can fail in the following ways
   *
   * IOException - If there is an underlying IOException when trying to open
   *               the directory on disk where the Lucene index is persisted.
   *
   * It might return Failure with other exceptions depending on which methods
   * on IndexWriter `f` is using.
   */
  protected def doWithWriter(project: IProject)(f: IndexWriter => Unit): Try[Unit] = {
    val loc = SearchPlugin.plugin.get.indexLocationForProject(project)
    using(FSDirectory.open(loc), handlers = IOToTry[Unit]) { dir =>
      using(new IndexWriter(dir, config), handlers = IOToTry[Unit]) { writer =>
        Try(f(writer))
      }
    }
  }

  /**
   * ARM method for searching the index. Can fail in the following ways
   *
   * IOException - If there is an underlying IOException when trying to open
   *               the directory on disk where the Lucene index is persisted.
   *
   * It might return Failure with other exceptions depending on which methods
   * on IndexSearcher `f` is using.
   */
  protected def withSearcher[A](project: IProject)(f: IndexSearcher => A): Try[A] = {
    val loc = SearchPlugin.plugin.get.indexLocationForProject(project)
    using(FSDirectory.open(loc), handlers = IOToTry[A]) { dir =>
      using(DirectoryReader.open(dir), handlers = IOToTry[A]) { reader =>
        val searcher = new IndexSearcher(reader)
        Try(f(searcher))
      }
    }
  }

  /**
   * Catch all IOException's and convert them to Failures
   */
  private def IOToTry[X]: Catch[Try[X]] = {
    Ex.catching(classOf[IOException]).toTry
  }

  /**
   * Collection of Term's that are used in multiple queries.
   */
  protected object Terms {

    def projectTerm(project: IProject) = {
      new Term(LuceneFields.PROJECT_NAME, project.getName)
    }

    def pathTerm(path: IPath) = {
      new Term(LuceneFields.PATH, path.toPortableString())
    }
  }

  /**
   * Create a Lucene document based on the information stored in the occurrence.
   */
  protected def toDocument(project: IProject, o: Occurrence): Document = {
    import LuceneFields._

    val doc = new Document

    def persist(key: String, value: String) =
      doc.add(new Field(key, value,
          Field.Store.YES, Field.Index.NOT_ANALYZED))

    persist(WORD, o.word)
    persist(PATH, o.file.workspaceFile.getProjectRelativePath().toPortableString())
    persist(OFFSET, o.offset.toString)
    persist(OCCURRENCE_KIND, o.occurrenceKind.toString)
    persist(PROJECT_NAME, project.getName)

    doc
  }

  /**
   * Converts a Lucene Document into an Occurrence. Returns Left[InvalidDocument] if
   * the Lucene document isn't valid and Left[MissingFile] if the file that is being
   * referenced no longer exists. Otherwise it returns Right[Occurrence].
   *
   * A Lucene document is invalid if it doesn't contain the fields we expect.
   *
   */
  protected def fromDocument(doc: Document): Either[ConversionError, Occurrence] = {
    import LuceneFields._
    (for {
      word           <- Option(doc.get(WORD))
      path           <- Option(doc.get(PATH))
      offset         <- Option(doc.get(OFFSET))
      occurrenceKind <- Option(doc.get(OCCURRENCE_KIND))
      projectName    <- Option(doc.get(PROJECT_NAME))
    } yield {
      val root = ResourcesPlugin.getWorkspace().getRoot()
      (for {
        project        <- Option(root.getProject(projectName))
        ifile          <- Option(project.getFile(Path.fromPortableString(path)))
        file           <- Util.scalaSourceFileFromIFile(ifile)
      } yield {
        Occurrence(word, file, Integer.parseInt(offset), OccurrenceKind.fromString(occurrenceKind))
      }).fold {
        // The file or project apparently no longer exists. This can happen
        // if the project/file has been deleted/renamed and a search is
        // carried out before the Eclipse Resource Events are propagated.
        Left(MissingFile(path, projectName)): Either[ConversionError, Occurrence]
      }(x => Right(x))
    }).fold {
      // The Lucene document didn't contain the fields we expected. It is very
      // unlikely this will happen but let's delete the document to be sure.
      Left(InvalidDocument(doc)): Either[ConversionError, Occurrence]
    }(x => x)
  }

}