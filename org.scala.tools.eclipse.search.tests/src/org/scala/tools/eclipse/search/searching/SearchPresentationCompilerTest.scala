package org.scala.tools.eclipse.search.searching

import scala.tools.eclipse.testsetup.SDTTestUtils
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.scala.tools.eclipse.search.TestUtil
import SearchPresentationCompilerTest.Project
import org.scala.tools.eclipse.search.FileChangeObserver
import java.util.concurrent.CountDownLatch
import org.eclipse.core.resources.IFile
import org.junit.Ignore

class SearchPresentationCompilerTest {

  import SearchPresentationCompilerTest._

  private val project = Project("SearchPresentationCompilerTest")

  @After
  def deleteProject() {
    project.delete
  }

  @Test
  def canGetSymbolAtLocation {
    project.create("ExampleWithSymbol.scala") {"""
      class ExampleWithSymbol {
        val |x: Int = 42
      }
    """
    } expectedSymbolNamed(Some("x"))
  }

  @Test
  def constructorDefinition {
    project.create("ConstructorDefinition.scala") {"""
      class A {
        def th|is(x: String) {
          this()
        }
      }
    """} expectedSymbolNamed(Some("<init>"))
  }

  @Test
  def constructorInvocation {
    project.create("ConstructorReference.scala") {"""
      class A {
        def this(x: String) {
          this()
        }
      }
      object A {
        ne|w A("test")
      }
    """} expectedSymbolNamed(Some("<init>"))
  }

  @Test
  def noSymbolIfAskedForWrongLocation {
    project.create("ExampleWithSymbol2.scala") {"""
      class ExampleWithSymbol2 {
        val x: Int = 42
      }
      |
    """
    } expectedNoSymbol
  }

  @Test
  def notTypeableIfAskedForLocationWithTypeError {
    project.create("ExampleWithSymbol3.scala") {"""
      class ExampleWithSymbol3 {
        def foo(x: String) = x
        def bar(x: String) = invalid(fo|o(x))
      }
    """} expectedTypeError
  }

  @Test
  def notTypeableIfAskedForLocationWithTypeErrorClasses {
    project.create("NotTypeableClassA.scala") {"""
      class A {
        def fo|o(x: String) = x
      }
    """}

    project.create("NotTypeableClassB.scala") {"""
      class B {
        def bar = invalid((new A).fo|o("test"))
      }
    """} expectedTypeError
  }

  @Test
  def isSameMethod_withSameSymbol {
    project.create("WithSameSymbol.scala") {"""
      class WithSameSymbol {
        def re|ve|rse(x: String) = x.reverse
      }
    """} isSameMethod(true)
  }

  @Test
  def isSameMethod_referenceAndDeclaration {
    project.create("ReferenceAndDeclaration.scala") {"""
      class ReferenceAndDeclaration {
        def fo|o(x: String) = x
        def bar(x: String) = fo|o(x)
      }
    """} isSameMethod(true)
  }

  @Test
  def isSameMethod_referenceAndDeclarationWithError{
    project.create("WithError.scala") {"""
      class ReferenceAndDeclaration {
        def fo|o(x: String) = x
        def bar(x: String) = invalid(fo|o(x))
      }
    """} isSameMethod(false)
  }

  @Test
  def isSameMethod_referenceAndDeclarationInSeperateClass {
    project.create("ReferenceAndDeclarationInSeperateClass.scala") {"""
      class A {
        def fo|o(x: String) = x
      }
      class B {
        def bar(x: String) = (new A).fo|o(x)
      }
    """} isSameMethod(true)
  }

  @Test
  def isSameMethod_falseForDifferentMethods {
    project.create("FalseForDifferentMethods.scala") {"""
      class FalseForDifferentMethods {
        def ad|dStrings(x: String, y: String) = x + y
        def ad|dInts(x: Int, y: Int) = x + y
      }
    """} isSameMethod(false)
  }

  @Test def isSameMethod_worksForApply {
    project.create("WorksForApply.scala") {"""
      object ObjectA {
        def a|pply(x: String) = x
      }
      object ObjectB {
        Obje|ctA("test")
      }
    """} isSameMethod(true)
  }

  @Test
  def isSameMethod_overriddenCountsAsSame {
    project.create("OverriddenCountsAsSame.scala") {"""
      class A {
        def fo|o(x: String) = x
      }
      class B extends A {
        override def fo|o(x: String) = x
      }
    """} isSameMethod(true)
  }

  @Test
  def isSameMethod_overriddenValCountsAsSame {
    project.create("OverriddenCountsAsSame.scala") {"""
      class A {
        def fo|o: String = "hi"
      }
      class B extends A {
        override val fo|o: String = "there"
      }
    """} isSameMethod(true)
  }

  @Test
  def isSameMethod_overriddenVarCountAsSame {
    project.create("OverriddenVarCountsAsSame.scala") {"""
      trait A {
        def fo|o_=(x: String): Unit
      }
      abstract class B extends A {
        var f|oo: String
      }
    """} isSameMethod(true)
  }

  @Test
  def isSameMethod_typeAliases {
    project.create("TypeAliases.scala") {"""
      class A {
        def fo|o: String = "hi"
      }
      class B extends A {
        type S = String
        override def fo|o: S = "there"
      }
    """} isSameMethod(true)
  }

  @Test
  def isSameMethod_canDistinguishOverloadedMethods {
    project.create("CanDistinguishOverloadedMethods.scala") {"""
      class A {
        def ad|d(x: String, y: String) = x + y
        def ad|d(x: Int, y: Int) = x + y
      }
    """} isSameMethod(false)
  }

  @Test
  def isSameMethod_partiallyAppliedMethod {
    project.create("PartiallyApplied.scala") {"""
      trait A {
        def fo|o(x: String): String
        def bar: String => String = fo|o
      }
    """} isSameMethod(true)
  }

  @Test
  def isSameMethod_canHandleSubclasses {
    // It needs to be able to
    project.create("CanHandleSubclasses.scala") {"""
      class A {
        def fo|o(x: String): String = x
      }
      class B extends A {
        def bar = fo|o("test")
      }
    """} isSameMethod(true)
  }

  @Test
  def isSameMethod_canHandleTraits {
    project.create("CanHandleTraits.scala") {"""
      trait A {
        def fo|o(x: String): String = x
      }
      class B extends A {
        def bar = fo|o("test")
      }
    """} isSameMethod(true)
  }

  @Test
  def isSameMethod_abstractOverrideDeclaration {
    project.create("AbstractOverrideDeclaration.scala") {"""
      trait A {
        def fo|o(x: String): String
      }
      class B extends A {
        abstract override def fo|o(x: String): String = "Test"
      }
    """} isSameMethod(true)
  }

  @Test
  def isSameMethod_abstractOverrideSuperReference {
    project.create("AbstractOverrideDeclaration.scala") {"""
      trait A {
        def fo|o(x: String): String
      }
      trait B extends A {
        abstract override def foo(x: String): String = {
          super.f|oo(x)+"Test"
        }
      }
    """} isSameMethod(true)
  }

  @Test
  def isSameMethod_thisTypes {
    project.create("ThisTypes.scala") {"""
      class A {
        def m|e: this.type = this
      }
      class B extends A {
        override def m|e: this.type = this
      }
    """} isSameMethod(true)
  }

  @Test
  def isSameMethod_casting {
    project.create("Casting.scala") {"""
      class A {
        def fo|o(x: String): String = x
        def bar = (new Object).asInstanceOf[A].fo|o("test")
      }
    """} isSameMethod(true)
  }

  @Test def isSameMethod_constructorsAreMethodsToo {
    project.create("Constructors.scala") {"""
      class A {
        def th|is(x: String) {
          this()
        }
      }
      class B {
        def foo = ne|w A("test")
      }
    """} isSameMethod(true)
  }

  @Test
  def isSameMethod_constructorInvocation {
    project.create("ConstructorReference.scala") {"""
      class A {
        def t|his(x: String) {
          this()
        }
        def this(x: String, y: String){
          t|his(x)
        }
      }
    """} isSameMethod(true)
  }

  @Test def isSameMethod_selfTypes {
    project.create("Constructors.scala") {"""
      trait A {
        def fo|o(x: String): String = x
      }
      class B { this: A =>
        def bar = fo|o("test")
      }
    """} isSameMethod(true)
  }

  @Test
  def isSameMethod_canHandleRenamedMethods {
    project.create("CanHandleRenamedMethods.scala") {"""
      class C {
        import C.{ foo => bar }
        val y = b|ar("hi")
      }
      object C {
        def fo|o(x: String) = x
      }
    """} isSameMethod(true)
  }

  @Test
  def isSameMethod_worksWithSameProject {

    val p1 = Project("SearchPresentationCompilerTest-worksWithSameProject")

    val latch = new CountDownLatch(2)
    val observer = FileChangeObserver(p1.scalaProject)(onAdded = _ => latch.countDown)

    val sourceA = p1.create("A.scala")("""
      class A {
        def f|oo(x: String) = x.reverse
      }""")

    val sourceB = p1.create("B.scala")("""
      class B {
        def bar(x: String) = (new A()).fo|o("test")
      }""")

    latch.await(5,java.util.concurrent.TimeUnit.SECONDS)

    sourceA.isSameMethodAs(sourceB)

    p1.delete
    observer.stop
  }

  @Test
  def isSameMethod_workWithDifferentProjects {
    /*
     * Test that we can compare symbols from two different projects.
     *
     * In this case project p2 depends on p1 and uses a class A defined in p1. We
     * want to make sure that it can compare the references to members of A in
     * project p2 with A in p1
     */

    val p1 = Project("SearchPresentationCompilerTest-workWithDifferentProjects-1")
    val p2 = Project("SearchPresentationCompilerTest-workWithDifferentProjects-2")

    val latch = new CountDownLatch(2)

    val observer1 = FileChangeObserver(p1.scalaProject)(onAdded = _ => latch.countDown)
    val observer2 = FileChangeObserver(p2.scalaProject)(onAdded = _ => latch.countDown)

    p2.addProjectsToClasspath(p1)

    val sourceA = p1.create("A.scala")("""
      class A {
        def f|oo(x: String) = x.reverse
      }""")

    val sourceB = p2.create("B.scala")("""
      class B {
        def bar(x: String) = (new A()).fo|o("test")
      }""")

    latch.await(5,java.util.concurrent.TimeUnit.SECONDS)

    sourceA.isSameMethodAs(sourceB)

    p1.delete
    p2.delete
    observer1.stop
    observer2.stop
  }

  @Test
  def isSameMethod_worksWithDifferentProjectsNotOnCP {
    /*
     * In this case we have
     *
     *     (P1)
     *     /  \
     *    /    \
     *  (P2)  (P3)
     *
     * Now, we want to make sure that using the PC of P3 we can compare symbols between
     * P3 and P2 given that they're defined in P1.
     */
    val p1 = Project("SearchPresentationCompilerTest-worksWithDifferentProjectsNotOnCP-1")
    val p2 = Project("SearchPresentationCompilerTest-worksWithDifferentProjectsNotOnCP-2")
    val p3 = Project("SearchPresentationCompilerTest-worksWithDifferentProjectsNotOnCP-3")

    p2.addProjectsToClasspath(p1)
    p3.addProjectsToClasspath(p1)

    val latch = new CountDownLatch(3)

    val observer1 = FileChangeObserver(p1.scalaProject)(onAdded = _ => latch.countDown)
    val observer2 = FileChangeObserver(p2.scalaProject)(onAdded = _ => latch.countDown)
    val observer3 = FileChangeObserver(p3.scalaProject)(onAdded = _ => latch.countDown)

    val sourceA = p1.create("A.scala")("""
      class A {
        def f|oo(x: String) = x.reverse
      }""")

    val sourceB = p2.create("B.scala")("""
      class B {
        def bar(x: String) = (new A()).fo|o("test")
      }""")

    val sourceC = p3.create("C.scala")("""
      class C {
        def bar(x: String) = (new A()).fo|o("test")
      }""")

    latch.await(5,java.util.concurrent.TimeUnit.SECONDS)

    sourceB.isSameMethodAs(sourceA)
    sourceC.isSameMethodAs(sourceA)
    sourceC.isSameMethodAs(sourceB)

    p1.delete
    p2.delete
    p3.delete
    observer1.stop
    observer2.stop
    observer3.stop
  }
}

object SearchPresentationCompilerTest
     extends TestUtil
        with SourceCreator
