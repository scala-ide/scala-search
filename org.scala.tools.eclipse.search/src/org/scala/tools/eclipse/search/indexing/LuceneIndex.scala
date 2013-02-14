package org.scala.tools.eclipse.search.indexing

import java.io.File

import scala.tools.eclipse.logging.HasLogger

import org.apache.lucene.analysis.core.SimpleAnalyzer
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.util.Version
import scala.collection.JavaConverters._

class LuceneIndex(indexLocation: File) extends HasLogger {

  private val dir      = FSDirectory.open(indexLocation)
  private val analyzer = new SimpleAnalyzer(Version.LUCENE_41)
  private val config   = new IndexWriterConfig(Version.LUCENE_41, analyzer);

  /**
   * Add all `occurrences` to the index.
   */
  def addOccurrences(occurrences: Seq[Occurrence]) = {
    doWithWriter { writer =>
      val docs = occurrences.map( _.toDocument )
      writer.addDocuments(docs.toIterable.asJava)
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