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
import org.scalaide.core.testsetup.TestProjectSetup
import org.apache.lucene.document.StringField

class LuceneIntegrationTest {

  import LuceneIntegrationTest._

  @Test def integration() {
    val dir = FSDirectory.open(INDEX_DIR.toPath())
    val analyzer = new SimpleAnalyzer()
    val config = new IndexWriterConfig(analyzer);
    val writer = new IndexWriter(dir, config)
    val doc = new Document
    doc.add(new StringField("test", "it works!", Field.Store.YES))
    writer.addDocument(doc)
    writer.close()
    val reader = DirectoryReader.open(dir)
    val d = reader.document(0)

    assertEquals("Should be able to store and read a document", doc.get("test"), d.get("test"))
  }
}

object LuceneIntegrationTest extends TestUtil {
  val INDEX_DIR = new File(mkPath("target","lucene-integration-test-index"))

}