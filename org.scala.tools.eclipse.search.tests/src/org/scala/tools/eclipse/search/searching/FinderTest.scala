package org.scala.tools.eclipse.search.searching

import org.scalaide.core.testsetup.SDTTestUtils
import org.scala.tools.eclipse.search.indexing.Index
import org.scala.tools.eclipse.search.indexing.SourceIndexer
import org.eclipse.core.runtime.Path
import org.scala.tools.eclipse.search.TestUtil
import org.junit.Test
import org.junit.Assert._
import org.scalaide.core.internal.jdt.model.ScalaCompilationUnit
import org.eclipse.core.runtime.NullProgressMonitor
import java.util.concurrent.CountDownLatch
import org.scala.tools.eclipse.search.FileChangeObserver
import org.scala.tools.eclipse.search.LogErrorReporter
import org.scalaide.core.IScalaProject
import org.apache.lucene.search.IndexSearcher
import scala.util.Try
import scala.util.Failure
import java.io.IOException
import org.junit.After
import org.junit.Before
import org.scala.tools.eclipse.search.TypeEntity
import org.scalaide.logging.HasLogger
import org.junit.Ignore

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
    val location = Location(sourceA.unit, sourceA.markers.head)
    find(finder, location, Scope(Set(project.scalaProject))) { hit =>
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
    val location = Location(sourceA.unit, sourceA.markers.head)
    find(finder, location, Scope(Set(project.scalaProject))) { hit =>
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

    val loc1 = Location(sourceA.unit, sourceA.markers(0))
    val loc2 = Location(sourceA.unit, sourceA.markers(1))

    find(finder, loc1, Scope(Set(project.scalaProject))) { hit =>
      resultsForGetter += 1
      hitLatch.countDown
    }

    find(finder, loc2, Scope(Set(project.scalaProject))) { hit =>
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
    val location = Location(sourceA.unit, sourceA.markers.head)
    find(finder, location, Scope(Set(project1.scalaProject, project2.scalaProject))) { hit =>
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
  def findAllSubclassesWorksWithClasses = subclassesNamed("WithsWithClasses"){"""
    class |Foo
    class |Bar extends Foo
  """}(List("Bar"))

  @Test
  def findAllSubclassesWorksWithTraits = subclassesNamed("WorksWithTraits"){"""
    trait |Foo
    trait |Bar extends Foo
  """}(List("Bar"))

  @Test
  def findAllSubclassesIgnoresSelftypes = subclassesNamed("FindAllSubclassesIgnoresSelftypes"){"""
    trait |Foo
    trait Bar { this: Foo => }
  """}(Nil)

  @Test
  def findAllSubclassesIgnoresTypeConstructorArguments = subclassesNamed("FindAllSubclassesIgnoresTypeConstructorArguments"){"""
    trait Bar[A]
    trait |Foo extends Bar[Foo]
  """}(Nil) // I.e. make sure that Foo doesn't count as a subtype of Foo.

  @Test
  def findAllSubclasses_worksWithTypeArguments = subclassesNamed("FindAllSubclassesIgnoresTypeConstructorArguments"){"""
    trait |Bar[A]
    trait |Foo extends Bar[Foo]
  """}(List("Foo"))

  @Test
  def findAllSubclasses_worksWithNestedTypeConstructors = subclassesNamed("FindAllSubclassesWorksWithNestedTypeConstructors") {"""
    trait |Foo[A]
    trait Bar extends Foo[Foo[String]]
  """}(List("Bar")) // make sure Foo isn't listed twice.

  @Test
  def findAllSubclasses_worksWithCollidingNames = subclassesNamed("WorksWithCollidingNames") {"""
    trait |A
    trait B extends A
    object B extends A
    trait C extends B
  """}(List("B"))

  @Test
  def findAllSubclasses_worksWithTypesFromSTDLib = subclassesNamed("FindAllSubclassesWorksWithTypesFromSTDLib") {"""
    trait StringOrdering extends |Ordering[String]
  """}(List("StringOrdering"))

  @Ignore("Ticket #1001818")
  @Test
  def findAllSubclasses_findAnonymousInstantiations = subclassesNamed("FindAllSubclassesFindAnonymousInstantiations") {"""
      object Container {
        trait Foo
        trait Bar extends Foo
        trait |FooImpl { this: Foo => }
        new Bar with FooImpl
      }
  """}(List("new Bar with FooImpl"))

  @Test
  def findAllSubclasses_canUseTypeParameterOfMethod = subclassesNamed("FindAllSubclassesCanUseTypeParameterOfMethod") {"""
    class A
    class B extends A {
      classOf[|A]
    }
  """}(List("B"))

  @Test
  def findAllSubclasses_typeParametersAreNotSuperTypes = subclassesNamed("FindAllSubclassesTypeParametersAreNotSuperTypes") {"""
    class |A
    abstract class B extends Function1[A,Unit]
  """}(Nil)

  /*
   * -----------------
   * findSupertypes
   * -----------------
   */

  @Test
  def findSuperclassesWorksWithClasses = superclassesNamed("WithsWithClasses"){"""
    class Foo
    class |Bar extends Foo
  """}(List("Foo"))

  @Test
  def findSuperclassesWorksWithTraits = superclassesNamed("WorksWithTraits"){"""
    trait Foo
    trait |Bar extends Foo
  """}(List("Foo", "Object"))

  @Test
  def findSuperclassesDoesntCountSelfTypesAsSuperType = superclassesNamed("FindSuperclassesDoesntCountSelfTypesAsSuperType"){"""
    trait Foo
    trait |Bar { this: Foo => }
  """}(List("Object"))

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
      override protected def withSearcher[A](project: IScalaProject)(f: IndexSearcher => A): Try[A] = {
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

    val location = Location(sourceA.unit, sourceA.markers.head)
    finder.entityAt(location).right.toOption.flatten map { entity =>
      finder.occurrencesOfEntityAt(entity, Scope(Set(project1.scalaProject, project2.scalaProject)), new NullProgressMonitor)(
        handler = _ => {
          hits += 1
          hitLatch.countDown
        },
        errorHandler = _ => {
          failures += 1
          hitLatch.countDown
        })
    }

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
    val location = Location(sourceA.unit, sourceA.markers.head)
    find(finder, location, Scope(Set(project.scalaProject))) {
      case Certain(_) => {
        hits += 1
        hitLatch.countDown
      }
      case Uncertain(_) => {
        potentialHits += 1
        hitLatch.countDown
      }
    }

    hitLatch.await(5, java.util.concurrent.TimeUnit.SECONDS)

    assertEquals(s"Expected the search to find 1 hit, but found ${hits}", 1, hits)
    assertEquals(s"Expected the search to find 1 potential hit, but found ${potentialHits}", 1, potentialHits)

    project.delete
    observer.stop
  }

}

object FinderTest extends TestUtil
    with SourceCreator
    with HasLogger {

  val INDEX_DIR = new Path(mkPath("target", "FinderTestIndex"))

  def anonymousIndexer: SourceIndexer =  {
    val index = new Index {
      override val base = INDEX_DIR
    }
    new SourceIndexer(index)
  }

  def anonymousFinder(index: Index): Finder =  new Finder(index, new LogErrorReporter)

  def subclassesNamed(name: String)(text: String)(names: List[String]): Unit = {
    val project = Project(s"FindAllSubclassesNamedTest-$name")

    val indexer = anonymousIndexer
    val finder = anonymousFinder(indexer.index)

    val source = project.create(s"$name.scala")(text)

    indexer.indexProject(project.scalaProject)

    var hitNames = List[String]()

    val location = Location(source.unit, source.markers.head)
    finder.entityAt(location).right.toOption.flatten foreach {
      case entity: TypeEntity => finder.findSubtypes(entity, Scope(Set(project.scalaProject)), new NullProgressMonitor) { hit =>
        hitNames = hit.value.name +: hitNames
      }
      case x => fail(s"Expected a subclass of TypeEntity, but got $x")
    }

    project.delete

    assertEquals(names.sorted, hitNames.sorted)
  }

  def superclassesNamed(name: String)(text: String)(names: List[String]): Unit = {
    val project = Project(s"FindAllSuperclassesNamedTest-$name")

    val indexer = anonymousIndexer
    val finder = anonymousFinder(indexer.index)

    val source = project.create(s"$name.scala")(text)

    indexer.indexProject(project.scalaProject)

    var hitNames = List[String]()

    val location = Location(source.unit, source.markers.head)
    finder.entityAt(location).right.toOption.flatten foreach {
      case entity: TypeEntity => entity.supertypes foreach { supertype =>
        hitNames = supertype.name +: hitNames
      }
      case x => fail(s"Expected a subclass of TypeEntity, but got $x")
    }

    project.delete

    assertEquals(names.sorted, hitNames.sorted)
  }

  def find(finder: Finder, loc: Location, scope: Scope)(f: Confidence[Hit] => Unit): Unit = {
    finder.entityAt(loc).right.toOption.flatten map { entity =>
      finder.occurrencesOfEntityAt(entity, scope, new NullProgressMonitor)(
        handler = h => f(h)
      )
    } getOrElse fail("Couldn't get entity")
  }

}
