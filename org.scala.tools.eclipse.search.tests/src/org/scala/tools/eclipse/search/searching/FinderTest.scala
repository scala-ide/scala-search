package org.scala.tools.eclipse.search.searching

import scala.tools.eclipse.testsetup.SDTTestUtils
import org.scala.tools.eclipse.search.indexing.Index
import org.scala.tools.eclipse.search.indexing.SourceIndexer
import org.eclipse.core.runtime.Path
import org.scala.tools.eclipse.search.TestUtil
import org.junit.Test
import org.junit.Assert._
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.eclipse.EclipseUserSimulator
import org.eclipse.core.runtime.NullProgressMonitor
import java.util.concurrent.CountDownLatch
import org.scala.tools.eclipse.search.FileChangeObserver
import org.scala.tools.eclipse.search.LogErrorReporter
import scala.tools.eclipse.ScalaProject
import org.apache.lucene.search.IndexSearcher
import scala.util.Try
import scala.util.Failure
import java.io.IOException
import org.junit.After
import org.junit.Before

class FinderTest {

  /*
   * This won't have very many tests. The most interesting parts are in the components
   * that are used by the Finder, and they have separate tests. E.g. see
   * SearchPresentationCompiler for how symbols are compared.
   */

  import FinderTest._

  def anonymousIndex: Index with SourceIndexer =  new Index with SourceIndexer {
    override val base = INDEX_DIR
  }
  
  def anonymousFinder(index: Index): Finder =  new Finder(index, new LogErrorReporter)
  
  @Test
  def canFindOccurrencesInSameProject = {
    val index = anonymousIndex
    val finder = anonymousFinder(index)
    val project = Project("FinderTest")

    val latch = new CountDownLatch(2)
    val observer = FileChangeObserver(project.scalaProject)(onAdded = _ => latch.countDown)

    val sourceA = project.create("A.scala")("""
      class A {
        def f|oo(x: String) = x.reverse
      }
    """)

    val sourceB = project.create("B.scala")("""
      class B {
        def bar = (new A).f|oo("test")
      }
    """)

    latch.await(5, java.util.concurrent.TimeUnit.SECONDS)

    index.indexProject(project.scalaProject)

    @volatile var results = 0
    finder.occurrencesOfEntityAt(Location(sourceA.unit, sourceA.markers.head)) { loc =>
      results += 1
    }

    assertEquals(s"Checking references of method foo, Found ${results}", 2, results)

    project.delete
    observer.stop
  }

  @Test
  def canFindOccurrencesOfApply = {

    val config = new Index with SourceIndexer with Finder with LogErrorReporter {
      override val base = INDEX_DIR
    }

    val project = Project("FinderTest-Apply")

    val latch = new CountDownLatch(2)
    val observer = FileChangeObserver(project.scalaProject)(onAdded = _ => latch.countDown)

    val sourceA = project.create("A.scala")("""
      object ObjectA {
        def a|pply(x: String) = x
      }
      object ObjectB {
        Obje|ctA("test")
        ObjectA.apply("test")
      }
    """)

    latch.await(5, java.util.concurrent.TimeUnit.SECONDS)

    config.indexProject(project.scalaProject)

    @volatile var results = 0
    config.occurrencesOfEntityAt(Location(sourceA.unit, sourceA.markers.head)) { loc =>
      results += 1
    }

    assertEquals(s"Checking references of method foo, Found ${results}", 3, results)

    project.delete
    observer.stop
  }

  @Test
  def canFindOccurrencesInDifferentProjects = {
    val index = anonymousIndex
    val finder = anonymousFinder(index)
    val project1 = Project("FinderTest-DifferentProjects1")
    val project2 = Project("FinderTest-DifferentProjects2")

    project2.addProjectsToClasspath(project1)

    val latch = new CountDownLatch(2)
    val observer1 = FileChangeObserver(project1.scalaProject)(onAdded = _ => latch.countDown)
    val observer2 = FileChangeObserver(project2.scalaProject)(onAdded = _ => latch.countDown)

    val sourceA = project1.create("A.scala")("""
      class A {
        def f|oo(x: String) = x.reverse
      }
    """)

    val sourceB = project2.create("B.scala")("""
      class B {
        def bar = (new A).f|oo("test")
      }
    """)

    latch.await(5, java.util.concurrent.TimeUnit.SECONDS)

    index.indexProject(project1.scalaProject)
    index.indexProject(project2.scalaProject)

    @volatile var results = 0
    finder.occurrencesOfEntityAt(Location(sourceA.unit, sourceA.markers.head)) { loc =>
      results += 1
    }

    assertEquals(s"Checking references of method foo, Found ${results}", 2, results)

    project1.delete
    project2.delete
    observer1.stop
    observer2.stop
  }

  @Test
  def reportsSearchFailures = {
    /*
     * Should the Index break in some way that can't be handled lower
     * in the hierarchy, we want to know about it.
     */

    val project1 = Project("FinderTest-IndexFailure1")
    val project2 = Project("FinderTest-IndexFailure2")
    val index = new Index with SourceIndexer {
      override val base = INDEX_DIR
      // Emulate a failure for the index of project2.
      override protected def withSearcher[A](project: ScalaProject)(f: IndexSearcher => A): Try[A] = {
        if (project == project2.scalaProject) {
          Failure(new IOException)
        } else super.withSearcher(project)(f)
      }
    }
    val finder = anonymousFinder(index)

    project2.addProjectsToClasspath(project1)

    val latch = new CountDownLatch(2)
    val observer1 = FileChangeObserver(project1.scalaProject)(onAdded = _ => latch.countDown)
    val observer2 = FileChangeObserver(project2.scalaProject)(onAdded = _ => latch.countDown)

    val sourceA = project1.create("A.scala")("""
      class A {
        def f|oo(x: String) = x.reverse
      }
    """)

    val sourceB = project2.create("B.scala")("""
      class B {
        def bar = (new A).f|oo("test")
      }
    """)

    latch.await(5, java.util.concurrent.TimeUnit.SECONDS)

    index.indexProject(project1.scalaProject)
    index.indexProject(project2.scalaProject)

    @volatile var hits = 0
    @volatile var failures = 0
    finder.occurrencesOfEntityAt(Location(sourceA.unit, sourceA.markers.head))(
      hit = _ => hits += 1,
      errorHandler = _ => failures += 1)

    assertEquals(s"Expected the search to find 1 hit, but found ${hits}", 1, hits)
    assertEquals(s"Expected the search to have 1 error, but got ${failures}", 1, failures)

    project1.delete
    project2.delete
    observer1.stop
    observer2.stop
  }

  @Test
  def reportsUntypeableOccurrences = {

    /*
     * When a occurrence is found in the index and we can't type-check
     * the given point we want to be able to report that occurrence as
     * a potential hit.
     */
    val index = anonymousIndex
    val finder = anonymousFinder(index)

    val project = Project("FinderTest-UntableableOccurrence")

    val latch = new CountDownLatch(2)
    val observer = FileChangeObserver(project.scalaProject)(onAdded = _ => latch.countDown)

    val sourceA = project.create("A.scala")("""
      class A {
        def fo|o(x: String) = x
        def bar(x: String) = invalid(foo(x))
      }
    """)

    latch.await(5, java.util.concurrent.TimeUnit.SECONDS)

    index.indexProject(project.scalaProject)

    @volatile var hits = 0
    @volatile var potentialHits = 0
    finder.occurrencesOfEntityAt(Location(sourceA.unit, sourceA.markers.head))(
      hit = loc => hits += 1,
      potentialHit = loc => potentialHits += 1)

    assertEquals(s"Expected the search to find 1 hit, but found ${hits}", 1, hits)
    assertEquals(s"Expected the search to find 1 potential hit, but found ${potentialHits}", 1, potentialHits)

    project.delete
    observer.stop
  }

}

object FinderTest extends TestUtil
    with SourceCreator {

  val INDEX_DIR = new Path(mkPath("target", "FinderTestIndex"))

}
