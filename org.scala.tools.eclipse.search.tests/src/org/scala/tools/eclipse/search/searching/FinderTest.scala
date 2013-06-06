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

  @Test
  def canFindOccurrencesInSameProject = {
    val indexer = anonymousIndexer
    val finder = anonymousFinder(indexer.index)
    val project = Project("FinderTest")

    val EXPECTED_HITS_COUNT = 2
    val hitLatch = new CountDownLatch(EXPECTED_HITS_COUNT)
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

    indexer.indexProject(project.scalaProject)

    @volatile var results = 0
    finder.occurrencesOfEntityAt(Location(sourceA.unit, sourceA.markers.head), new NullProgressMonitor) { loc =>
      results += 1
      hitLatch.countDown
    }

    hitLatch.await(5, java.util.concurrent.TimeUnit.SECONDS)

    assertEquals(s"Checking references of method foo, Found ${results}", EXPECTED_HITS_COUNT, results)

    project.delete
    observer.stop
  }

  @Test
  def canFindOccurrencesOfApply = {
    val indexer = anonymousIndexer
    val finder = anonymousFinder(indexer.index)

    val project = Project("FinderTest-Apply")

    val EXPECTED_HITS_COUNT = 3
    val hitLatch = new CountDownLatch(EXPECTED_HITS_COUNT)
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

    indexer.indexProject(project.scalaProject)

    @volatile var results = 0
    finder.occurrencesOfEntityAt(Location(sourceA.unit, sourceA.markers.head), new NullProgressMonitor) { loc =>
      results += 1
      hitLatch.countDown
    }

    hitLatch.await(5, java.util.concurrent.TimeUnit.SECONDS)

    assertEquals(s"Checking references of method foo, Found ${results}", EXPECTED_HITS_COUNT, results)

    project.delete
    observer.stop
  }

  @Test def canFindOccurrencesOfExplicitSetters = {

    val indexer = anonymousIndexer
    val finder = anonymousFinder(indexer.index)

    val project = Project("FinderTest-CanCompareExplicitSetter")

    val latch = new CountDownLatch(1)
    val hitLatch = new CountDownLatch(4)
    val observer = FileChangeObserver(project.scalaProject)(onAdded = _ => latch.countDown)

    val sourceA = project.create("CanCompareExplicitSetter.scala") {"""
      object A {
        var varia|ble: String = "test"
      }
      object B {
        A.variab|le_=("foo")
      }
    """}

    latch.await(5, java.util.concurrent.TimeUnit.SECONDS)

    indexer.indexProject(project.scalaProject)

    @volatile var resultsForGetter = 0
    @volatile var resultsForSetter = 0
    finder.occurrencesOfEntityAt(Location(sourceA.unit, sourceA.markers(0)), new NullProgressMonitor) { loc =>
      resultsForGetter += 1
      hitLatch.countDown
    }
    finder.occurrencesOfEntityAt(Location(sourceA.unit, sourceA.markers(1)), new NullProgressMonitor) { loc =>
      resultsForSetter += 1
      hitLatch.countDown
    }

    hitLatch.await(5, java.util.concurrent.TimeUnit.SECONDS)

    assertEquals(s"Checking references of getter, Found ${resultsForGetter}", 2, resultsForGetter)
    assertEquals(s"Checking references of setter, Found ${resultsForSetter}", 2, resultsForSetter)

    project.delete
    observer.stop
  }

  @Test
  def canFindOccurrencesInDifferentProjects = {
    val indexer = anonymousIndexer
    val finder = anonymousFinder(indexer.index)
    val project1 = Project("FinderTest-DifferentProjects1")
    val project2 = Project("FinderTest-DifferentProjects2")

    project2.addProjectsToClasspath(project1)

    val EXPECTED_HITS_COUNT = 2
    val hitLatch = new CountDownLatch(EXPECTED_HITS_COUNT)
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

    indexer.indexProject(project1.scalaProject)
    indexer.indexProject(project2.scalaProject)

    @volatile var results = 0
    finder.occurrencesOfEntityAt(Location(sourceA.unit, sourceA.markers.head), new NullProgressMonitor) { loc =>
      results += 1
      hitLatch.countDown
    }

    hitLatch.await(5, java.util.concurrent.TimeUnit.SECONDS)

    assertEquals(s"Checking references of method foo, Found ${results}", EXPECTED_HITS_COUNT, results)

    project1.delete
    project2.delete
    observer1.stop
    observer2.stop
  }

  /*
   * -----------------
   * findAllSubclasses
   * -----------------
   */

  @Test
  def findAllSubclassesWorksWithClasses = subclassTest("WithsWithClasses"){"""
    class |Foo
    class |Bar extends Foo
  """}

  @Test
  def findAllSubclassesWorksWithTraits = subclassTest("WorksWithTraits"){"""
    trait |Foo
    trait |Bar extends Foo
  """}

  @Test
  def findAllSubclassesWorksWithSelfTypes = subclassTest("WorksWithSelfTypes"){"""
    trait |Foo
    trait |Bar { this: Foo => }
  """}

  /*
   * -------------------
   * Error Handling
   * -------------------
   */

  @Test
  def reportsSearchFailures = {
    /*
     * Should the Index break in some way that can't be handled lower
     * in the hierarchy, we want to know about it.
     */

    val project1 = Project("FinderTest-IndexFailure1")
    val project2 = Project("FinderTest-IndexFailure2")
    val index = new Index {
      override val base = INDEX_DIR
      // Emulate a failure for the index of project2.
      override protected def withSearcher[A](project: ScalaProject)(f: IndexSearcher => A): Try[A] = {
        if (project == project2.scalaProject) {
          Failure(new IOException)
        } else super.withSearcher(project)(f)
      }
    }
    val indexer = new SourceIndexer(index)
    val finder = anonymousFinder(index)

    project2.addProjectsToClasspath(project1)

    val hitLatch = new CountDownLatch(2)
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

    indexer.indexProject(project1.scalaProject)
    indexer.indexProject(project2.scalaProject)

    @volatile var hits = 0
    @volatile var failures = 0
    finder.occurrencesOfEntityAt(Location(sourceA.unit, sourceA.markers.head), new NullProgressMonitor)(
      hit = _ => {
        hits += 1
        hitLatch.countDown
      },
      errorHandler = _ => {
        failures += 1
        hitLatch.countDown
      })

    hitLatch.await(5, java.util.concurrent.TimeUnit.SECONDS)

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
    val indexer = anonymousIndexer
    val finder = anonymousFinder(indexer.index)

    val project = Project("FinderTest-UntableableOccurrence")

    val hitLatch = new CountDownLatch(2)
    val latch = new CountDownLatch(2)
    val observer = FileChangeObserver(project.scalaProject)(onAdded = _ => latch.countDown)

    val sourceA = project.create("A.scala")("""
      class A {
        def fo|o(x: String) = x
        def bar(x: String) = invalid(foo(x))
      }
    """)

    latch.await(5, java.util.concurrent.TimeUnit.SECONDS)

    indexer.indexProject(project.scalaProject)

    @volatile var hits = 0
    @volatile var potentialHits = 0
    finder.occurrencesOfEntityAt(Location(sourceA.unit, sourceA.markers.head), new NullProgressMonitor)(
      hit = loc => {
        hits += 1
        hitLatch.countDown
      },
      potentialHit = loc => {
        potentialHits += 1
        hitLatch.countDown
      })

    hitLatch.await(5, java.util.concurrent.TimeUnit.SECONDS)

    assertEquals(s"Expected the search to find 1 hit, but found ${hits}", 1, hits)
    assertEquals(s"Expected the search to find 1 potential hit, but found ${potentialHits}", 1, potentialHits)

    project.delete
    observer.stop
  }

}

object FinderTest extends TestUtil
    with SourceCreator {

  val INDEX_DIR = new Path(mkPath("target", "FinderTestIndex"))

  def anonymousIndexer: SourceIndexer =  {
    val index = new Index {
      override val base = INDEX_DIR
    }
    new SourceIndexer(index)
  }

  def anonymousFinder(index: Index): Finder =  new Finder(index, new LogErrorReporter)

  // Convenience method to create tests for finding subclasses. The `text` simply
  // needs to contain some class declarations. The first marker should be placed at
  // the class to find sub-types of. The rest of the markers represent the expected
  // results (i.e. a marker at a declaration means that you expect to find that
  // type as a sub-type of the first marker).
  def subclassTest(name: String)(text: String): Unit = {
    val project = Project(s"FindAllSubclassesTest-$name")

    val indexer = anonymousIndexer
    val finder = anonymousFinder(indexer.index)

    val source = project.create(s"$name.scala")(text)

    indexer.indexProject(project.scalaProject)

    var hitsOffsets = List[Int]()
    finder.findAllSubclasses(Location(source.unit, source.markers.head), new NullProgressMonitor) { hit =>
      if (source.markers.contains(hit.offset)) {
        hitsOffsets = hit.offset +: hitsOffsets
      }
    }

    val notFound = source.markers.tail.filter(!hitsOffsets.contains(_))

    project.delete

    assertEquals(
        s"Expected it to find subclasses at all markers, but didn't find: ${notFound}, found ${hitsOffsets}", 
        true, notFound.isEmpty)
  }

}
