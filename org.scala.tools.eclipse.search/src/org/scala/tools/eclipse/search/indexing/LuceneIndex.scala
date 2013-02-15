package org.scala.tools.eclipse.search.indexing

import java.io.File
import scala.collection.JavaConverters.asJavaIterableConverter
import scala.tools.eclipse.logging.HasLogger
import org.apache.lucene.analysis.core.SimpleAnalyzer
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.util.Version
import org.eclipse.core.resources.IFile
import org.apache.lucene.search.TermQuery
import org.apache.lucene.index.Term

/**
 * A Lucene based index of all occurrences of Scala entities recorded
 * in the workspace. See `org.scala.tools.eclipse.search.Document.toDocument`
 * for more information about what is stored.
 */
class LuceneIndex(indexLocation: File) extends HasLogger {

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
      val query = new TermQuery(new Term(LuceneFields.FILE, file.getAbsolutePath()))
      writer.deleteDocuments(query)
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

}