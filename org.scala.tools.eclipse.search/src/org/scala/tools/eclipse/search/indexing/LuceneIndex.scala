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

/**
 * A Lucene based index of all occurrences of Scala entities recorded
 * in the workspace. See `org.scala.tools.eclipse.search.Document.toDocument`
 * for more information about what is stored.
 */
class LuceneIndex(indexLocation: File) extends HasLogger {

  // TODO: Should also close dir at some point.
  private val dir      = FSDirectory.open(indexLocation)
  private val analyzer = new SimpleAnalyzer(Version.LUCENE_41)
  private val config   = new IndexWriterConfig(Version.LUCENE_41, analyzer);

  /**
   * Add all `occurrences` to the index.
   */
  def addOccurrences(occurrences: Seq[Occurrence]): Unit = {
    doWithWriter { writer =>
      val docs = occurrences.map( _.toDocument )
      writer.addDocuments(docs.toIterable.asJava)
    }
  }

  /**
   * Removed all occurrences from the index that are recorded in the
   * given file
   */
  def removeOccurrencesFromFile(file: File): Unit = {
    doWithWriter { writer =>
      val query = new TermQuery(TermQueries.fileTerm(file))
      writer.deleteDocuments(query)
    }
  }

  /**
   * Returns all occurrences recorded in the index for the given file. Mostly useful
   * for testing purposes.
   */
  def occurrencesInFile(file: File): Seq[Occurrence] = {
    withSearcher { searcher =>
      val query = new TermQuery(TermQueries.fileTerm(file))
      val hits = searcher.search(query, 10000).scoreDocs // TODO: Assumes at most 10k occurrences in a document.
      hits.map { hit =>
        val doc = searcher.doc(hit.doc)
        Occurrence.fromDocument(doc)
      }
    }
  }

  /**
   * ARM method for writing to the index.
   */
  private def doWithWriter(f: IndexWriter => Unit): Unit = {
    val writer = new IndexWriter(dir, config)
    f(writer)
    writer.close()
  }

  /**
   * ARM method for searching the index.
   */
  private def withSearcher[A](f: IndexSearcher => A): A = {
    val reader = DirectoryReader.open(dir)
    val searcher = new IndexSearcher(reader)
    val r = f(searcher)
    reader.close()
    r
  }

  /**
   * Collection of Term's that are used in multiple queries.
   */
  private object TermQueries {
    def fileTerm(file: File) = {
      new Term(LuceneFields.FILE, file.getAbsolutePath())
    }
  }

}