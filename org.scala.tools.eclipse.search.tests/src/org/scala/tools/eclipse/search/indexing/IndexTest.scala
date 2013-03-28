package org.scala.tools.eclipse.search.indexing

import java.io.File
import scala.tools.eclipse.testsetup.TestProjectSetup
import org.junit.Assert._
import org.junit.Test
import org.mockito.Mockito._
import org.eclipse.core.resources.IProject
import org.apache.lucene.index.IndexWriter
import scala.util.Try
import org.apache.lucene.document.Document
import java.io.IOException
import scala.util.Failure
import org.apache.lucene.search.BooleanQuery
import org.eclipse.core.runtime.Path
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.index.CorruptIndexException
import scala.util.Success
import org.apache.lucene.index.IndexWriter
import org.scala.tools.eclipse.search.TestUtil
import scala.collection.JavaConverters.asJavaIterableConverter

/**
 * Tests that the correct things are stored in the LuceneIndex. We shouldn't
 * require that many tests for this as it is the responsibility of the OccurrenceCollector
 * to record the correct information.
 */
class IndexTest {

  import LuceneIndexTest._

  @Test def storeAndRetrieve() {
    val index = new TestIndex(INDEX_DIR)
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
        source.scalaProject.underlying)

    assertTrue(results.isSuccess)
    val resultOccurrences = results.get.filter( x => interestingNames.contains(x.word) )
    assertEquals("Should be able to store and retrieve occurrences", expected, resultOccurrences)
  }

  @Test def deleteOccurrences() {

    val index = new TestIndex(INDEX_DIR)
    val indexer = new SourceIndexer(index)
    val source = scalaCompilationUnit(mkPath("org","example","ScalaClass.scala"))
    indexer.indexScalaFile(source)

    index.removeOccurrencesFromFile(source.workspaceFile.getProjectRelativePath(), source.scalaProject.underlying)

    val results = index.occurrencesInFile(
        source.workspaceFile.getProjectRelativePath(),
        source.scalaProject.underlying)

    assertTrue(results.isSuccess)
    assertEquals("Index should not contain any occurrence in file", 0, results.get.size)

  }

  @Test def ioExceptionsByLuceneAreCaughtWhenAddingOccurrences() {
    val index = mockedWriterIndex(new IOException)
    val r = index.addOccurrences(Nil, project.underlying)
    failWithExceptionOfKind[IOException](r)
  }

  @Test def ioExceptionsByLuceneAreCaughtWhenRemovingOccurrences() {
    val index = mockedWriterIndex(new IOException)
    val r = index.removeOccurrencesFromFile(anonymousPath, project.underlying)
    failWithExceptionOfKind[IOException](r)
  }

  @Test def ioExceptionsByLuceneAreCaughtWhenSearchingForOccurrences() {
    val index = mockedReaderIndex
    val r = index.occurrencesInFile(anonymousPath, project.underlying)
    failWithExceptionOfKind[IOException](r)
  }

  @Test def corruptIndexExceptionByLuceneAreCaughtWhenAddingOccurrences() {
    val index = mockedWriterIndex(new CorruptIndexException(""))
    val r = index.addOccurrences(Nil, project.underlying)
    failWithExceptionOfKind[CorruptIndexException](r)
  }

  @Test def corruptIndexExceptionByLuceneAreCaughtWhenRemovingOccurrences() {
    val index = mockedWriterIndex(new CorruptIndexException(""))
    val r = index.removeOccurrencesFromFile(anonymousPath, project.underlying)
    failWithExceptionOfKind[CorruptIndexException](r)
  }

  @Test def dontShowOccurrenceInFilesThatNoLongerExist() {
    // scenario: At some point we indexed a file. The file is now deleted and before the events were
    // propagated so the index could remove the occurrence in that file a user performed a search.
    // In such a case we don't want to the index to return occurrences from the index that has
    // been recorded in files that no longer exists
    val source = scalaCompilationUnit(mkPath("org","example","DoesNotExist.scala"))
    val occurrence = Occurrence("", source, 0, Reference)
    val index = new TestIndex(INDEX_DIR)
    index.addOccurrences(Seq(occurrence), project.underlying)
    val s = index.occurrencesInFile(new Path("org/example/DoesNotExist.scala"), project.underlying)
    assertTrue(s.isSuccess)
    assertTrue(s.get.isEmpty)
  }

}

object LuceneIndexTest extends TestProjectSetup("lucene_index_test_project", bundleName= "org.scala.tools.eclipse.search.tests")
                          with TestUtil {

  import scala.collection.JavaConverters.asJavaIterableConverter

  def anonymousPath = new Path(".")

  def failWithExceptionOfKind[A <: Exception](t: Try[_])(implicit m: Manifest[A]) = {
    t match {
      case Failure(ex: A) => assertTrue(true)
      case Failure(ex) => fail(s"Expected a Failure wrapping exception of type ${m} but got ${ex.getClass}")
      case x@Success(_) => fail(s"Expected a Failure wrapping exception of type ${m} but got ${x}")
    }
  }

  val INDEX_DIR = new File(mkPath("target","lucene-index-test"))

  def mockedWriterIndex(ex: Exception) = new TestIndex(INDEX_DIR) {
    override def doWithWriter(project: IProject)(f: IndexWriter => Unit): Try[Unit] = {
      val writer = mock(classOf[IndexWriter])

      when(writer.deleteDocuments(
          org.mockito.Matchers.argThat(mocks.args.anyInstance[BooleanQuery]))
      ).thenThrow(ex)

      when(writer.addDocuments(List[Document]().toIterable.asJava)).thenThrow(ex)

      Try(f(writer))
    }
  }

  def mockedReaderIndex = new TestIndex(INDEX_DIR) {
    override def withSearcher[A](project: IProject)(f: IndexSearcher => A): Try[A] = {
      val searcher = mock(classOf[IndexSearcher])
      when(searcher.search(
          org.mockito.Matchers.argThat(mocks.args.anyInstance[BooleanQuery]),
          org.mockito.Matchers.anyInt())
      ).thenThrow(new IOException())

      Try(f(searcher))
    }
  }

}