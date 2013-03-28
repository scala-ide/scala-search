package org.scala.tools.eclipse.search.indexing

import java.io.File
import scala.Array.fallbackCanBuildFrom
import scala.collection.JavaConverters.asJavaIterableConverter
import scala.tools.eclipse.logging.HasLogger
import org.apache.lucene.analysis.core.SimpleAnalyzer
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.Term
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.TermQuery
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.util.Version
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.scala.tools.eclipse.search.Util
import org.eclipse.core.resources.IProject
import org.scala.tools.eclipse.search.SearchPlugin
import org.scala.tools.eclipse.search.ScalaSearchException
import scala.util.Try
import org.scala.tools.eclipse.search.ScalaSearchException
import scala.util.Failure
import scala.util.Success
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.runtime.Path
import org.scala.tools.eclipse.search.using
import org.eclipse.core.runtime.IPath
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.BooleanClause

/**
 * A Lucene based index of all occurrences of Scala entities recorded
 * in the workspace. See `toDocument` for more information about what
 * is stored in the index.
 *
 * A separate Lucene index is stored on disk for each project in the
 * Workspace.
 * 
 * This class assumes that the resources passed to the different 
 * methods exist and, in the case of a IProject, it's open.
 *
 * This class is thread-safe.
 */
class Index(indicesRoot: File) extends HasLogger {

  private def config = {
    val analyzer = new SimpleAnalyzer(Version.LUCENE_41)
    new IndexWriterConfig(Version.LUCENE_41, analyzer)
  }

  private val MAX_POTENTIAL_MATCHES = 100000

  /**
   * Add all `occurrences` to the index of the specific project.
   */
  def addOccurrences(occurrences: Seq[Occurrence], project: IProject): Unit = {
    doWithWriter(project) { writer =>
      val docs = occurrences.map( toDocument(project, _) )
      writer.addDocuments(docs.toIterable.asJava)
    }
  }

  /**
   * Removed all occurrences from the index that are recorded in the
   * given file
   */
  def removeOccurrencesFromFile(path: IPath, project: IProject): Unit = {
    doWithWriter(project) { writer =>
      val query = new BooleanQuery()
      query.add(new TermQuery(Terms.pathTerm(path)), BooleanClause.Occur.MUST)
      query.add(new TermQuery(Terms.projectTerm(project)), BooleanClause.Occur.MUST)
      writer.deleteDocuments(query)
    }
  }

  /**
   * Returns all occurrences recorded in the index for the given file. Mostly useful
   * for testing purposes.
   */
  def occurrencesInFile(path: IPath, project: IProject): Seq[Occurrence] = {
    withSearcher(project) { searcher =>
      val query = new BooleanQuery()
      query.add(new TermQuery(Terms.pathTerm(path)), BooleanClause.Occur.MUST)
      query.add(new TermQuery(Terms.projectTerm(project)), BooleanClause.Occur.MUST)
      val hits = searcher.search(query, MAX_POTENTIAL_MATCHES).scoreDocs
      hits.map { hit =>
        val doc = searcher.doc(hit.doc)
        fromDocument(doc)
      }
    }
  }

  /**
   * ARM method for writing to the index.
   */
  private def doWithWriter(project: IProject)(f: IndexWriter => Unit): Unit = {
    val loc = SearchPlugin.plugin.indexLocationForProject(project)
    using(FSDirectory.open(loc)) { dir =>
      using(new IndexWriter(dir, config)) { writer => 
        f(writer)  
      }
    }
  }

  /**
   * ARM method for searching the index.
   */
  private def withSearcher[A](project: IProject)(f: IndexSearcher => A): A = {
    val loc = SearchPlugin.plugin.indexLocationForProject(project)
    using(FSDirectory.open(loc)) { dir =>
      using(DirectoryReader.open(dir)) { reader =>
        val searcher = new IndexSearcher(reader)
        f(searcher)
      }
    }
  }

  /**
   * Collection of Term's that are used in multiple queries.
   */
  private object Terms {

    def projectTerm(project: IProject) = {
      new Term(LuceneFields.PROJECT_NAME, project.getName)
    }

    def pathTerm(path: IPath) = {
      new Term(LuceneFields.PATH, path.toPortableString())
    }
  }

  /**
   * Create a Lucene document based on the information stored in the
   * occurrence.
   */
  private def toDocument(project: IProject, o: Occurrence): Document = {
    import LuceneFields._
    val doc = new Document
    doc.add(new Field(WORD, o.word, Field.Store.YES, Field.Index.NOT_ANALYZED))
    doc.add(new Field(PATH, o.file.workspaceFile.getProjectRelativePath().toPortableString(), Field.Store.YES, Field.Index.NOT_ANALYZED))
    doc.add(new Field(OFFSET, o.offset.toString, Field.Store.YES, Field.Index.NOT_ANALYZED))
    doc.add(new Field(OCCURRENCE_KIND, o.occurrenceKind.toString, Field.Store.YES, Field.Index.NOT_ANALYZED))
    doc.add(new Field(PROJECT_NAME, project.getName, Field.Store.YES, Field.Index.NOT_ANALYZED))
    doc
  }

  /**
   * Converts a Lucene Document into an Occurrence. Will throw exception if
   * there are things that can't be converted to the expected type.
   */
  def fromDocument(doc: Document): Occurrence = {
    import LuceneFields._
    (for {
      word           <- Option(doc.get(WORD))
      path           <- Option(doc.get(PATH))
      offset         <- Option(doc.get(OFFSET))
      occurrenceKind <- Option(doc.get(OCCURRENCE_KIND))
      projectName    <- Option(doc.get(PROJECT_NAME))
    } yield {
      val root = ResourcesPlugin.getWorkspace().getRoot()

      val project = Option(root.getProject(projectName)).getOrElse(
          throw new ScalaSearchException(s"No project named ${projectName} in the workspace"))

      val ifile = Option(project.getFile(Path.fromPortableString(path))).getOrElse(
          throw new ScalaSearchException(s"No file exists at ${path} in the project ${projectName}"))

      val file = Util.scalaSourceFileFromIFile(ifile).getOrElse(
          throw new ScalaSearchException(s"Wasn't able to create ScalaSourceFile from path ${path}"))

      Occurrence(word, file, Integer.parseInt(offset), OccurrenceKind.fromString(occurrenceKind))

    }) getOrElse {
      throw new ScalaSearchException("Wasn't able to convert document to occurrence")
    }
  }

}