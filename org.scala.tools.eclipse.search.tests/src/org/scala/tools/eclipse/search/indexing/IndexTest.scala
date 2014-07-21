package org.scala.tools.eclipse.search.indexing

import java.io.IOException
import scala.collection.JavaConverters.asJavaIterableConverter
import org.scalaide.core.api.ScalaProject
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import org.apache.lucene.document.Document
import org.apache.lucene.index.CorruptIndexException
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.IndexSearcher
import org.eclipse.core.runtime.Path
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.when
import org.scala.tools.eclipse.search.TestUtil
import org.scala.tools.eclipse.search.searching.SourceCreator
import LuceneIndexTest.Project
import java.util.concurrent.CountDownLatch
import org.scala.tools.eclipse.search.FileChangeObserver
import org.scala.tools.eclipse.search.searching.Scope

/**
 * Tests that the correct things are stored in the LuceneIndex. We shouldn't
 * require that many tests for this as it is the responsibility of the OccurrenceCollector
 * to record the correct information.
 */
class IndexTest {

  import LuceneIndexTest._

  private val project  = Project("IndexTest-Common")
  private val projectA = Project("IndexTest-ProjectA")
  private val projectB = Project("IndexTest-ProjectB")

  @Before
  def before {
    projectA.create("S.scala") {"""
      class A {
        def foo(x: String) = "bar"
      }
    """}
    projectB.create("S.scala") {"""
      class B {
        def bar(x: String) = {
          val a = new A()
          a.foo(x)
        }
      }
    """}
  }

  @After
  def after {
    project.delete
    projectA.delete
    projectB.delete
  }

  /**
   * Tests index adding/removing
   */

  @Test
  def storeAndRetrieve() {

    val project = Project("DeleteOccurrences")
    val index   = TestIndex("DeleteOccurrences")
    val indexer = new SourceIndexer(index)

    val source = project.create("A.scala") {"""
      class A {
        def |method: String = {
          val s1 = |methodOne
          val s2 = |methodTwo(s1)
          |methodThree(s1)(s2)
        }
      }
      object A {
        def |foo = "Test"
      }
    """}

    indexer.indexProject(project.scalaProject)

    val results = index.occurrencesInFile(
        source.unit.workspaceFile.getProjectRelativePath(),
        project.scalaProject)

    val positions = source.markers
    val names     = List("method", "methodOne", "methodTwo", "methodThree", "foo")
    val expected  = positions zip names

    val resultOccurrences = results.get.filter( x => positions.contains(x.offset) ).map( x => (x.offset, x.word))

    project.delete

    assertTrue(results.isSuccess)
    assertEquals("Should be able to store and retrieve occurrences", expected.toList, resultOccurrences.toList)
  }

  @Test
  def deleteOccurrences() {

    val project = Project("DeleteOccurrences")
    val index   = TestIndex("DeleteOccurrences")
    val indexer = new SourceIndexer(index)

    val source = project.create("A.scala") {"""
      class ScalaClass {
        def method: String = {
          val s1 = methodOne
          val s2 = methodTwo(s1)
          methodThree(s1)(s2)
        }
      }
      object ScalaClass {
        def methodOne = "Test"
        def methodTwo(s: String) = s
        def methodThree(s: String)(s2: String) = s + s2
      }
    """}

    indexer.indexProject(project.scalaProject)

    index.removeOccurrencesFromFile(
        source.unit.workspaceFile.getProjectRelativePath(),
        project.scalaProject)

    val occurrences = index.occurrencesInFile(
        source.unit.workspaceFile.getProjectRelativePath(),
        project.scalaProject)

    project.delete

    assertTrue(occurrences.isSuccess)
    assertEquals("Index should not contain any occurrence in file", 0, occurrences.get.size)
  }

  @Test
  def findOccurrencesInSuperPosition {

    val project = Project("FindOccurrencesInSuperPosition")
    val index   = TestIndex("FindOccurrencesInSuperPosition")
    val indexer = new SourceIndexer(index)

    project.create("A.scala") {"class A"}
    project.create("B.scala") {"class B extends A"}
    project.create("C.scala") {"trait C { this: A => }"}

    indexer.indexProject(project.scalaProject)

    val (occurrences, _) = index.findOccurrencesInSuperPosition("A", Scope(Set(project.scalaProject)))

    assertEquals("A", 1, occurrences.size)
  }

   @Test
   def findDeclarationOfClass = {
     // The occurrence collector is responsible for finding the right things
     // to index. This test just shows that the index is capable of filtering
     // occurrences that are declarations. Hence we don't need to test it for
     // all kinds of declarations as they're treated in the same way (as long
     // as they're indexed)

     val project = Project("FindDeclarationOfClass")
     val index   = TestIndex("FindDeclarationOfClass")
     val indexer = new SourceIndexer(index)

     // Add a declaration and a reference. The reference is there to
     // make sure the references doesn't count as a declaration.
     project.create("A.scala") {"class A"}
     project.create("B.scala") {"class B extends A"}

     indexer.indexProject(project.scalaProject)

     val (occurrences, _) = index.findDeclarations("A", Scope(Set(project.scalaProject)))

     assertEquals("A", 1, occurrences.size)
   }

  /**
   * Tests exceptional states
   */

  @Test def ioExceptionsByLuceneAreCaughtWhenAddingOccurrences() {
    val index = mockedWriterConfig(new IOException)
    val r = index.addOccurrences(Nil, project.scalaProject)
    failWithExceptionOfKind[IOException](r)
  }

  @Test def ioExceptionsByLuceneAreCaughtWhenRemovingOccurrences() {
    val index = mockedWriterConfig(new IOException)
    val r = index.removeOccurrencesFromFile(anonymousPath, project.scalaProject)
    failWithExceptionOfKind[IOException](r)
  }

  @Test def ioExceptionsByLuceneAreCaughtWhenSearchingForOccurrences() {
    val index = mockedReaderConfig
    val r = index.occurrencesInFile(anonymousPath, project.scalaProject)
    failWithExceptionOfKind[IOException](r)
  }

  @Test def corruptIndexExceptionByLuceneAreCaughtWhenAddingOccurrences() {
    val index = mockedWriterConfig(new CorruptIndexException(""))
    val r = index.addOccurrences(Nil, project.scalaProject)
    failWithExceptionOfKind[CorruptIndexException](r)
  }

  @Test def corruptIndexExceptionByLuceneAreCaughtWhenRemovingOccurrences() {
    val index = mockedWriterConfig(new CorruptIndexException(""))
    val r = index.removeOccurrencesFromFile(anonymousPath, project.scalaProject)
    failWithExceptionOfKind[CorruptIndexException](r)
  }

  @Test def dontShowOccurrenceInFilesThatNoLongerExist() {
    // scenario: At some point we indexed a file. The file is now deleted and before the events were
    // propagated so the index could remove the occurrence in that file a user performed a search.
    // In such a case we don't want to the index to return occurrences from the index that has
    // been recorded in files that no longer exists
    val project = Project("DontShowOccurrenceInFilesThatNoLongerExist")
    val index   = TestIndex("DontShowOccurrenceInFilesThatNoLongerExist")

    val source = project.create("DoesNotExist.scala")("")
    val path = source.unit.workspaceFile.getProjectRelativePath()

    val occurrence = Occurrence("", source.unit, 0, Reference)

    index.addOccurrences(Seq(occurrence), project.scalaProject)

    // Make sure the file-deleted event is propagated.
    val latch = new CountDownLatch(1)
    val observer = FileChangeObserver(project.scalaProject)( onRemoved = _ => latch.countDown)
    project.delete
    latch.await(5, java.util.concurrent.TimeUnit.SECONDS)

    val result = index.occurrencesInFile(path,project.scalaProject)

    project.delete

    assertTrue(s"Expected it to succeed but got $result", result.isSuccess)
    assertTrue(s"Expected to not find any results, but found ${result.get}", result.get.isEmpty)
  }

  /**
   * Tests searching
   */

  @Test def canFindPotentialOccurrencesInProject {
    val index = new Index {
      override val base = INDEX_DIR
    }
    val indexer = new SourceIndexer(index)

    indexer.indexProject(projectA.scalaProject)

    val (results, failures) = index.findOccurrences("foo", Scope(Set(projectA.scalaProject)))
    assertEquals(1, results.size)
    assertEquals(0, failures.size)
  }

  @Test def canFindPotentialOccurrencesInProjectClosure {
    val index = new Index {
      override val base = INDEX_DIR
    }
    val indexer = new SourceIndexer(index)

    indexer.indexProject(projectA.scalaProject)
    indexer.indexProject(projectB.scalaProject)

    val (results, failures) = index.findOccurrences("foo", Scope(Set(projectA.scalaProject, projectB.scalaProject)))
    assertEquals(2, results.size)
  }

  @Test def reportsErrorWhenSearching {

    val config = new Index {
      override val base = INDEX_DIR
      override def withSearcher[A](project: ScalaProject)(f: IndexSearcher => A): Try[A] = {
        Failure(new CorruptIndexException(""))
      }
    }

    val (results, failures) = config.findOccurrences("foo", Scope(Set(projectA.scalaProject)))
    assertEquals(0, results.size)
    assertEquals(1, failures.size)
    assertEquals(BrokenIndex(projectA.scalaProject), failures.head)

  }

  @Test def oneProjectTestFailureDoesntAffectTheOthers {
    // Test that you can get search results for one project even though
    // searching in another project failed
    val index = new Index {
      override val base = INDEX_DIR
      override def withSearcher[A](project: ScalaProject)(f: IndexSearcher => A): Try[A] = {
        if (project.underlying.getName == projectA.scalaProject.underlying.getName) {
          Failure(new CorruptIndexException(""))
        }
        else super.withSearcher(project)(f)
      }
    }
    val indexer = new SourceIndexer(index)

    indexer.indexProject(projectA.scalaProject)
    indexer.indexProject(projectB.scalaProject)

    val (results, failures) = index.findOccurrences("foo", Scope(Set(projectA.scalaProject, projectB.scalaProject)))
    assertEquals(1, results.size)
    assertEquals(1, failures.size)
    assertEquals(BrokenIndex(projectA.scalaProject), failures.head)
  }

}

object LuceneIndexTest extends TestUtil
                          with SourceCreator {

  def anonymousPath = new Path(".")

  def failWithExceptionOfKind[A <: Exception](t: Try[_])(implicit m: Manifest[A]) = {
    t match {
      case Failure(ex: A) => assertTrue(true)
      case Failure(ex) => fail(s"Expected a Failure wrapping exception of type ${m} but got ${ex.getClass}")
      case x@Success(_) => fail(s"Expected a Failure wrapping exception of type ${m} but got ${x}")
    }
  }

  val INDEX_DIR = new Path(mkPath("target","lucene-index-test"))

  def mockedWriterConfig(ex: Exception): TestIndex = new TestIndex {

    override val base = INDEX_DIR

    override def doWithWriter(project: ScalaProject)(f: IndexWriter => Unit): Try[Unit] = {
      val writer = mock(classOf[IndexWriter])

      when(writer.deleteDocuments(
          org.mockito.Matchers.argThat(mocks.args.anyInstance[BooleanQuery]))
      ).thenThrow(ex)

      when(writer.addDocuments(List[Document]().toIterable.asJava)).thenThrow(ex)

      Try(f(writer))
    }
  }

  def mockedReaderConfig: TestIndex = new TestIndex {

    override val base = INDEX_DIR

    override def withSearcher[A](project: ScalaProject)(f: IndexSearcher => A): Try[A] = {
      val searcher = mock(classOf[IndexSearcher])
      when(searcher.search(
          org.mockito.Matchers.argThat(mocks.args.anyInstance[BooleanQuery]),
          org.mockito.Matchers.anyInt())
      ).thenThrow(new IOException())

      Try(f(searcher))
    }
  }

}