package org.scala.tools.eclipse.search

import java.io.File

import scala.tools.eclipse.testsetup.TestProjectSetup

import org.junit.Assert.assertEquals
import org.junit.Test
import org.scala.tools.eclipse.search.indexing.Declaration
import org.scala.tools.eclipse.search.indexing.Index
import org.scala.tools.eclipse.search.indexing.Occurrence
import org.scala.tools.eclipse.search.indexing.Reference
import org.scala.tools.eclipse.search.indexing.SourceIndexer

import LuceneIndexTest.mkPath
import LuceneIndexTest.scalaCompilationUnit

/**
 * Tests that the correct things are stored in the LuceneIndex. We shouldn't
 * require that many tests for this as it is the responsibility of the OccurrenceCollector
 * to record the correct information.
 */
class IndexTest {

  import LuceneIndexTest._

  @Test def storeAndRetrieve() {
    val index = new Index(INDEX_DIR)
    val indexer = new SourceIndexer(index)
    val source = scalaCompilationUnit(mkPath("org","example","ScalaClass.scala"))
    indexer.indexScalaFile(source)

    val expected = List(
      Occurrence("method",      source, 46,  Declaration),
      Occurrence("methodOne",   source, 78,  Reference),
      Occurrence("methodTwo",   source, 101, Reference),
      Occurrence("methodThree", source, 119, Reference),
      Occurrence("methodOne",   source, 172, Declaration),
      Occurrence("methodTwo",   source, 197, Declaration),
      Occurrence("methodThree", source, 228, Declaration)
    )

    val interestingNames = List("method", "methodOne", "methodTwo", "methodThree")

    val results = index.occurrencesInFile(
        source.workspaceFile.getProjectRelativePath(),
        source.scalaProject.underlying).filter( x => interestingNames.contains(x.word))

    assertEquals("Should be able to store and retrieve occurrences", expected, results)
  }

  @Test def deleteOccurrences() {

    val index = new Index(INDEX_DIR)
    val indexer = new SourceIndexer(index)
    val source = scalaCompilationUnit(mkPath("org","example","ScalaClass.scala"))
    indexer.indexScalaFile(source)

    index.removeOccurrencesFromFile(source.workspaceFile.getProjectRelativePath(), source.scalaProject.underlying)

    val results = index.occurrencesInFile(
        source.workspaceFile.getProjectRelativePath(),
        source.scalaProject.underlying)

    assertEquals("Index should not contain any occurrence in file", 0, results.size)

  }

}

object LuceneIndexTest extends TestProjectSetup("lucene_index_test_project", bundleName= "org.scala.tools.eclipse.search.tests")
                          with TestUtil {

  val INDEX_DIR = new File(mkPath("target","lucene-index-test"))

}