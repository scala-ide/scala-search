package org.scala.tools.eclipse.search

import java.io.File

import org.apache.lucene.analysis.core.SimpleAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.util.Version
import org.junit.Assert._
import org.junit.Test

object LuceneIntegrationTest {
  val INDEX_DIR = new File(path("target","lucene-test-index"))

  private def path(strings: String*) =
    strings.mkString(File.separator)
}

class LuceneIntegrationTest {

  import LuceneIntegrationTest._

  @Test def integration() {
    val dir = FSDirectory.open(INDEX_DIR)
    val analyzer = new SimpleAnalyzer(Version.LUCENE_41)
    val config = new IndexWriterConfig(Version.LUCENE_41, analyzer);
    val writer = new IndexWriter(dir, config)
    val doc = new Document
    doc.add(new Field("test", "it works!", Field.Store.YES, Field.Index.NOT_ANALYZED))
    writer.addDocument(doc)
    writer.close()
    val reader = DirectoryReader.open(dir)
    val d = reader.document(0)

    assertEquals("Should be able to store and read a document", doc.get("test"), d.get("test"))
  }
}