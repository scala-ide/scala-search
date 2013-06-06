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
import org.eclipse.core.resources.IFile
import scala.tools.eclipse.ScalaProject
import scala.util.Success
import scala.util.Failure
import scala.collection.mutable.ArraySeq

trait SearchFailure
case class BrokenIndex(project: ScalaProject) extends SearchFailure

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
 * This trait is thread-safe.
 */
trait Index extends HasLogger {

  def base: IPath

  def location(project: IProject): IPath = {
    base.append(project.getName())
  }

  /**
   * Checks if the `file` exists and is a type we know how to index.
   */
  def isIndexable(file: IFile): Boolean = {
    // TODO: https://scala-ide-portfolio.assembla.com/spaces/scala-ide/tickets/1001616
    Option(file).filter(_.exists).map( _.getFileExtension() == "scala").getOrElse(false)
  }

  def indexExists(project: IProject): Boolean = {
    location(project).toFile.exists
  }

  def deleteIndex(project: IProject): Try[Boolean] = {

    def deleteRec(f: File): Boolean = {
      if (f.isDirectory()) {
        val children = f.listFiles
        children.foreach(deleteRec)
      }
      f.delete
    }

    Try(deleteRec(location(project).toFile)).recover {
      case t: Throwable =>
        logger.debug(s"Exception while deleting index for project `${project.getName}`", t)
        false
    }
  }

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

  def findOccurrences(word: String, projects: Set[ScalaProject]): (Seq[Occurrence], Seq[SearchFailure]) = {
    findOccurrences(List(word), projects)
  }

  /**
   * Search the projects for all occurrences of the `word` that are in super-position, i.e.
   * mentioned as a super-class or self-type.
   */
  def findOccurrencesInSuperPosition(word: String, projects: Set[ScalaProject]): (Seq[Occurrence], Seq[SearchFailure]) = {

    val query = new BooleanQuery()
    query.add(new TermQuery(Terms.isInSuperPosition), BooleanClause.Occur.MUST)
    query.add(new TermQuery(Terms.exactWord(word)), BooleanClause.Occur.MUST)

    val resuts = queryProjects(query, projects)

    groupSearchResults(resuts.seq)
  }

  /**
   * Search the relevant project indices for all occurrences of the given words.
   *
   * This will return a sequence of all the occurrences it found in the index and a
   * sequence containing information about failed searches, if any. A search can fail
   * if the Index in inaccessible or broken.
   */
  def findOccurrences(words: List[String], projects: Set[ScalaProject]): (Seq[Occurrence], Seq[SearchFailure]) = {

    val query = new BooleanQuery()
    val innerQuery = new BooleanQuery()
    for { w <- words } {
      innerQuery.add(
          new TermQuery(Terms.exactWord(w)),
          BooleanClause.Occur.SHOULD)
    }
    query.add(innerQuery, BooleanClause.Occur.MUST)

    val resuts = queryProjects(query, projects)

    groupSearchResults(resuts.seq)
  }

  private def queryProjects(query: BooleanQuery, projects: Set[ScalaProject]): Set[(ScalaProject, Try[ArraySeq[Occurrence]])] = {
    projects.par.map { project =>
      val resultsForProject = withSearcher(project){ searcher =>
        for {
          hit        <- searcher.search(query, MAX_POTENTIAL_MATCHES).scoreDocs
          occurrence <- fromDocument(searcher.doc(hit.doc)).right.toOption
        } yield occurrence
      }
      (project, resultsForProject)
    }.seq
  }

  private def groupSearchResults(results: Set[(ScalaProject, Try[ArraySeq[Occurrence]])]): (Seq[Occurrence], Seq[SearchFailure]) = {
    val initial: (Seq[Occurrence], Seq[SearchFailure]) = (Nil, Nil)
    results.foldLeft(initial) { (acc, t: (ScalaProject,Try[Seq[Occurrence]])) =>
      val (occurrences, failures) = acc
      t match {
        case (_, Success(xs)) => (occurrences ++ xs, failures)
        case (p, Failure(_))  => (occurrences, BrokenIndex(p) +: failures)
      }
    }
  }

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
  def addOccurrences(occurrences: Seq[Occurrence], project: ScalaProject): Try[Unit] = {
    doWithWriter(project) { writer =>
      val docs = occurrences.map( toDocument(project.underlying, _) )
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
  def removeOccurrencesFromFile(path: IPath, project: ScalaProject): Try[Unit] = {
    doWithWriter(project) { writer =>
      val query = new BooleanQuery()
      query.add(new TermQuery(Terms.pathTerm(path)), BooleanClause.Occur.MUST)
      query.add(new TermQuery(Terms.projectTerm(project.underlying)), BooleanClause.Occur.MUST)
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
  protected def doWithWriter(project: ScalaProject)(f: IndexWriter => Unit): Try[Unit] = {
    val loc = location(project.underlying).toFile()
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
  protected def withSearcher[A](project: ScalaProject)(f: IndexSearcher => A): Try[A] = {
    val loc = location(project.underlying).toFile()
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

    def exactWord(word: String) = {
      new Term(LuceneFields.WORD, word)
    }

    def isInSuperPosition = {
      new Term(LuceneFields.IS_IN_SUPER_POSITION, "true")
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
    persist(LINE_CONTENT, o.lineContent.toString)
    persist(IS_IN_SUPER_POSITION, o.isInSuperPosition.toString)
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

    def convertToBoolean(str: String) = str match {
      case "true"  => true
      case "false" => false
      case x =>
        logger.debug(s"Expected true/false when converting document, but got $x")
        false
    }

    import LuceneFields._
    (for {
      word           <- Option(doc.get(WORD))
      path           <- Option(doc.get(PATH))
      offset         <- Option(doc.get(OFFSET))
      occurrenceKind <- Option(doc.get(OCCURRENCE_KIND))
      lineContent    <- Option(doc.get(LINE_CONTENT))
      projectName    <- Option(doc.get(PROJECT_NAME))
      isSuper        <- Option(doc.get(IS_IN_SUPER_POSITION)).map(convertToBoolean)
    } yield {
      val root = ResourcesPlugin.getWorkspace().getRoot()
      (for {
        project        <- Option(root.getProject(projectName))
        ifile          <- Option(project.getFile(Path.fromPortableString(path)))
        file           <- Util.scalaSourceFileFromIFile(ifile)
      } yield {
        Occurrence(word, file, Integer.parseInt(offset), OccurrenceKind.fromString(occurrenceKind), lineContent, isSuper)
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