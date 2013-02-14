package org.scala.tools.eclipse.search

import java.io.File

import scala.tools.eclipse.testsetup.TestProjectSetup

import org.junit.Assert.assertEquals
import org.junit.Test
import org.scala.tools.eclipse.search.indexing.Declaration
import org.scala.tools.eclipse.search.indexing.LuceneIndex
import org.scala.tools.eclipse.search.indexing.Method
import org.scala.tools.eclipse.search.indexing.Occurrence
import org.scala.tools.eclipse.search.indexing.Reference
import org.scala.tools.eclipse.search.indexing.SourceIndexer

import LuceneIndexTest.mkPath
import LuceneIndexTest.scalaCompilationUnit

/**
 * Tests that the correct things are stored in the LuceneIndex. We shouldn't
 * require that many tests for this as it is the responsiblity of the occurrence
 * finder to record the correct information.
 */
class LuceneIndexTest {

  import LuceneIndexTest._

  @Test def storeAndRetrieve() {
    val index = new LuceneIndex(INDEX_DIR)
    val indexer = new SourceIndexer(index)
    val source = scalaCompilationUnit(mkPath("org","example","ScalaClass.scala"))
    indexer.indexScalaFile(source)

    val expected = List(
      Occurrence("method",      source, 46,  Declaration, Method),
      Occurrence("methodOne",   source, 78,  Reference,   Method),
      Occurrence("methodTwo",   source, 101, Reference,   Method),
      Occurrence("methodThree", source, 119, Reference,   Method),
      Occurrence("methodOne",   source, 172, Declaration, Method),
      Occurrence("methodTwo",   source, 197, Declaration, Method),
      Occurrence("methodThree", source, 228, Declaration, Method)
    )

    val interestingNames = List("method", "methodOne", "methodTwo", "methodThree")

    val results = index.occurrencesInFile(source.file.file).filter( x => interestingNames.contains(x.word))

    assertEquals("Should be able to store and retrieve occurrences", expected, results)
  }

}

object LuceneIndexTest extends TestProjectSetup("lucene_index_test_project", bundleName= "org.scala.tools.eclipse.search.tests")
                          with TestUtil {

  val INDEX_DIR = new File(mkPath("target","lucene-index-test"))

}