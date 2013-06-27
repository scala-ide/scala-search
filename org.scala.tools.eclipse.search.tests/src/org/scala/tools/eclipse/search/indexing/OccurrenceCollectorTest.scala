package org.scala.tools.eclipse.search.indexing

import org.junit.Test
import org.junit.After
import org.junit.Assert._
import scala.tools.eclipse.testsetup.TestProjectSetup
import scala.util.Failure
import scala.util.Success
import org.scala.tools.eclipse.search.TestUtil
import org.scala.tools.eclipse.search.searching.SourceCreator
import scala.tools.eclipse.javaelements.ScalaSourceFile
import org.scala.tools.eclipse.search.Util

/**
 * This tests the occurrence collector exclusively, this doesn't depend on any for of index.
 */
class OccurrenceCollectorTest {

  import OccurrenceCollectorTest._

  private val project = Project("OccurrenceCollectorTest")

  @After
  def deleteProject() {
    project.delete
  }

  @Test
  def numberOfMethods = {
    val results = project.create("ScalaClass.scala") {"""
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
   """} occurrencesThatMatch { o => o.word == "methodOne" ||  o.word == "methodTwo" ||  o.word == "methodThree" }

    assertEquals("Should be 6 occurrences of foo and bar", 6, results.size)
  }

  @Test
  def methodChaining = {
    val results = project.create("MethodChaining.scala") {"""
      class MethodChaining {
        def foo() = this
        def bar() = this
      }

      object MethodChaining {
        def method() = {
          val x new MethodChaining()
          x.foo().bar()
        }
      }
   """} occurrencesThatMatch { o => o.word == "bar" ||  o.word == "foo" }

    assertEquals("Should be 2 occurrences of foo and bar", 4, results.size)
  }

  @Test def invocationAsArgument = {
    val results = project.create("InvocationAsArgument.scala") {"""
      class ScalaClass {
        def method: String = {
          val s2 = methodTwo(methodOne)
          methodThree(s1)(methodTwo(s2))
        }
      }
      object ScalaClass {
        def methodOne = "Test"
        def methodTwo(s: String) = s
        def methodThree(s: String)(s2: String) = s + s2
      }
   """} occurrencesThatMatch { o => o.word == "methodTwo" }

    assertEquals("Should be 3 occurrences of methodTwo", 3, results.size)
  }

  @Test def selectInApply() {
    val results = project.create("SelectInApply.scala") {"""
      class Foo {
        def bar(str: String) = str
      }

      class Bar {
        def x = "test"
      }

      object ScalaClass {
        val foo = new Foo
        val bar = new Bar
        foo.bar(bar.x)
      }
   """} occurrencesThatMatch { o => o.word == "x" }

    assertEquals("Should be 2 occurrences of x", 2, results.size)
  }

  @Test def stringInterpolation = {
    val results = project.create("StringInterpolation.scala") {"""
      object StringInterpolation {
        def foo(x: String) = s"Hi there, ${x}"
      }
   """} occurrencesThatMatch { o => o.word == "x" }

    assertEquals("Should be 2 occurrences of x", 2, results.size)
  }

  @Test def annotationsOnMethods = {
    val results = project.create("Annotations.scala") {"""
      import java.io.IOException

      object Test {
        @throws(classOf[IOException]) def test() = {}
      }
   """} occurrencesThatMatch { o => o.word == "IOException" }

    assertEquals("Should be 1 occurrences of IOException", 1, results.size)
  }

  @Test def recordsOccurrencesOfSyntheticEmptyConstructor = {
    val results = project.create("SyntheticEmptyConstructor.scala") {"""
      class SynthethicEmptyConstructor {}
   """} occurrencesThatMatch { o => o.word == "<init>" && o.occurrenceKind == Declaration }

    assertEquals("Should find 1 synthetic empty constructor", 1, results.size)
  }

  @Test def recordsOccurrencesOfSyntheticConstructor = {
    val results = project.create("SyntheticConstructor.scala") {"""
      class SyntheticConstructor(x: String) {}
   """} occurrencesThatMatch { o => o.word == "<init>" && o.occurrenceKind == Declaration }

    assertEquals("Should find 1 synthetic constructor", 1, results.size)
  }

  @Test def recordsOccurrencesOfExplicitConstructor = {
    // We expect 2 as the compiler will generate the default constructor.
    val results = project.create("ExplicitConstructor.scala") {"""
      class ExplicitConstructor {
        def this(s: String) = this
      }
   """} occurrencesThatMatch { o => o.word == "<init>" && o.occurrenceKind == Declaration }

    assertEquals("Should find 2 constructors", 2, results.size)
  }

  @Test
  def findInvocationsOfConstructors = {
    val source = project.create("ConstructorInvocations.scala") {"""
      class ConstructorInvocations
      object ConstructorInvocations {
        def apply() = |new ConstructorInvocations
      }
    """}

    val results = source.occurrencesThatMatch { o =>
      o.word == "<init>" &&
      o.occurrenceKind == Reference &&
      o.offset == source.markers.head
    }

    assertEquals("Should find 1 constructors", 1, results.size)
  }

  @Test
  def indexesTypesInSuperPosition = {
    val results = project.create("CanIndexTypesInSuperPosition.scala") {"""
      class A
      class B extends A with Object
    """} occurrencesThatMatch { o => o.isInSuperPosition && (o.word == "A" || o.word == "Object") }

    assertEquals("Should be 2 occurrence in super position", 2, results.size)
  }

  @Test
  def indexesSelfTypesAsSuperPosition = {
    val results = project.create("IndexesSelfTypesAsTypesInSuperPosition.scala") {"""
      class A
      trait B { this: A => }
    """} occurrencesThatMatch { o => o.isInSuperPosition && o.word == "A" }

    assertEquals("Should be 1 occurrence of A in super position", 1, results.size)
  }

  @Test
  def indexesObjectSuperTypes = {
    val results = project.create("IndexesSelfTypesAsTypesInSuperPosition.scala") {"""
      trait A
      object B extends A
    """} occurrencesThatMatch { o => o.isInSuperPosition && o.word == "A" }
    assertEquals("Should be 1 occurrence of A in super position", 1, results.size)
  }

  @Test
  def canHandleFilesBeingDeleted = {
    // Since everything is concurrent we can easily find ourselves in a
    // situation where the occurrence collector is used on a file that
    // doesn't exist anymore.
    val source = project.create("CanHandleFilesBeingDeleted.scala")("")
    val sf = Util.scalaSourceFileFromIFile(source.unit.workspaceFile).get
    source.unit.file.delete
    assertFalse("File should shouldn't exist at this point", sf.exists())
    val occ = OccurrenceCollector.findOccurrences(sf)
    assertTrue(s"It should've returned a Failure but got ${occ.getClass()}",occ.isFailure)
  }

}

object OccurrenceCollectorTest extends TestUtil with SourceCreator
