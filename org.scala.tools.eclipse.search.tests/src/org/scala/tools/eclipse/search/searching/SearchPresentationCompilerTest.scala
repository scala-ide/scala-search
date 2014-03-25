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
import org.osgi.framework.Version
import scala.tools.eclipse.ScalaPlugin

class SearchPresentationCompilerTest {

  import SearchPresentationCompilerTest._

  private val project = Project("SearchPresentationCompilerTest")

  @After
  def deleteProject() {
    project.delete
  }

  /**----------------------*
   * nameOfEntityAt
   * ----------------------*/

  @Test
  def nameOfEntityAt_worksForClasses = {
    project.create("NameOfEntityAtWorksForClasses.scala") {"""
      class |NameOfEntityAtWorksForClasses
    """
    } expectedSymbolNamed(Some("NameOfEntityAtWorksForClasses"))
  }

  @Test
  def nameOfEntityAt_worksForSetter = {
    project.create("NameOfEntityAtWorksForClasses.scala") {"""
      class NameOfEntityAtWorksForClasses {
        var x: Int
        |x_=(42)
      }
    """
    } expectedSymbolNamed(Some("x"))
  }

  @Test
  def nameOfEntityAt_worksForGetters = {
    project.create("NameOfEntityAtWorksForGetter.scala") {"""
      class NameOfEntityAtWorksForGetter {
        val x: Int = 42
        |x
      }
    """
    } expectedSymbolNamed(Some("x"))
  }

  @Test
  def nameOfEntityAt_worksForLocal = {
    project.create("NameOfEntityAtWorksForLocal.scala") {"""
      class NameOfEntityAtWorksForLocal {
        val |x: Int = 42
        x
      }
    """
    } expectedSymbolNamed(Some("x"))
  }

  @Test
  def nameOfEntityAt_correctlyDecodesNameForLocal = {
    project.create("NameOfEntityAtDecodesNameForLocal.scala") {"""
      class NameOfEntityAtDecodesNameForLocal {
        val |:: = 42
      }
    """
    } expectedSymbolNamed(Some("::"))
  }

  @Test
  def nameOfEntityAt_correctlyDecodesNameForGetter = {
    project.create("NameOfEntityAtDecodesNameForGetter.scala") {"""
      class NameOfEntityAtDecodesNameForGetter {
        var :: = 42
        |::
      }
    """
    } expectedSymbolNamed(Some("::"))
  }

  @Test
  def nameOfEntityAt_correctlyDecodesNameForSetter = {
    project.create("NameOfEntityAtDecodesNameForSetter.scala") {"""
      class NameOfEntityAtDecodesNameForSetter {
        var :: = 42
        |::_(43)
      }
    """
    } expectedSymbolNamed(Some("::"))
  }

  @Test
  def nameOfEntityAt_dontShowSelfTypeAsName = {
    project.create("NameOfEntityAtDontShowSelfTypeAsName.scala") {"""
      trait A
      trait |B { this: A => }
    """
    } expectedSymbolNamed(Some("B"))
  }

  @Test
  def nameOfEntityAt_canHandleSelfTypeAndSubTypeCycle = {
    project.create("NameOfEntityAtCanHandleSelfTypeAndSubTypeCycle.scala") {"""
      trait |Moo { self: Foo => }
      trait Foo extends Moo
    """} expectedSymbolNamed(Some("Moo"))
  }

  @Test
  def displayName_canHandleSelfTypeAndSubTypeCycle = {
    project.create("DisplayNameCanHandleSelfTypeAndSubTypeCycle.scala") {"""
      trait |Moo { self: Foo => }
      trait Foo extends Moo
    """} expectedDisplayName(Some("Moo"))
  }

  /**----------------------*
   * DisplayName
   * ----------------------*/

  @Test
  def dispalyName_doesntShowPackage = {
    project.create("DispalyNameDoesntShowPackage.scala") {"""
      package a {
        class |Foo
      }
    """
    } expectedDisplayName(Some("Foo"))
  }

  @Test
  def dispalyName_showsTypeParameters = {
    project.create("DispalyNameDoesntShowPackage.scala") {"""
      trait |Foo[A]
    """
    } expectedDisplayName(Some("Foo[A]"))
  }

  @Test
  def dispalyName_dontShowSelfTypeAsDisplayName = {
    project.create("NameOfEntityAtDontShowSelfTypeAsDisplayName.scala") {"""
      trait A
      trait |B { this: A => }
    """
    } expectedDisplayName(Some("B"))
  }

  @Test
  def dispalyName_showsNameOfObject = {
    project.create("NameOfEntityAtDontShowSelfTypeAsDisplayName.scala") {"""
      object |A
    """
    } expectedDisplayName(Some("A"))
  }

  @Test
  @Ignore("Ticket #1001820")
  def findSubclasses_CanHandleSelftypeFromOtherPackage = {
    project.create("DisplayNameOfEntityAtCanHandleSelftypeFromOtherPackage.scala") {"""
      package a {
        class A
      }
      package b {
        import a.A
        trait B { this: |A => }
      }
   """} expectedDisplayName(Some("A"))
  }

  /**----------------------*
   * Constructors
   * ----------------------*/

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

  /**----------------------*
   * Error Handling
   * ----------------------*/

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

  private def installedScalaVersionGreaterOrEqualsTo(version: Version) =
    ScalaPlugin.plugin.scalaLibBundle.getVersion().compareTo(version) >= 0

  @Test @Ignore("Flaky in 2.11")
  def notTypeableIfAskedForLocationWithTypeErrorClasses {
    if (installedScalaVersionGreaterOrEqualsTo(new Version(2, 11, 0))) {
      val sourceA = project.create("NotTypeableClassA.scala") {
        """
      class A {
        def fo|o(x: String) = x
      }
    """
      }

      project.create("NotTypeableClassB.scala") {
        """
      class B {
        def bar = invalid((new A).fo|o("test"))
      }
    """
      } isSameAs (sourceA, true)
    }
  }

  /**----------------------*
   * Methods               *
   * ----------------------*/

  @Test
  def isSameMethod_withSameSymbol {
    project.create("WithSameSymbol.scala") {"""
      class WithSameSymbol {
        def re|ve|rse(x: String) = x.reverse
      }
    """} isSame(true)
  }

  @Test
  def isSameMethod_referenceAndDeclaration {
    project.create("ReferenceAndDeclaration.scala") {"""
      class ReferenceAndDeclaration {
        def fo|o(x: String) = x
        def bar(x: String) = fo|o(x)
      }
    """} isSame(true)
  }

  @Test
  def isSameMethod_referenceAndDeclarationWithError{
    project.create("WithError.scala") {"""
      class ReferenceAndDeclaration {
        def fo|o(x: String) = x
        def bar(x: String) = invalid(fo|o(x))
      }
    """} isSame(false)
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
    """} isSame(true)
  }

  @Test
  def isSameMethod_falseForDifferentMethods {
    project.create("FalseForDifferentMethods.scala") {"""
      class FalseForDifferentMethods {
        def ad|dStrings(x: String, y: String) = x + y
        def ad|dInts(x: Int, y: Int) = x + y
      }
    """} isSame(false)
  }

  @Test def isSameMethod_worksForApply {
    project.create("WorksForApply.scala") {"""
      object ObjectA {
        def a|pply(x: String) = x
      }
      object ObjectB {
        Obje|ctA("test")
      }
    """} isSame(true)
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
    """} isSame(true)
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
    """} isSame(true)
  }

  @Test
  def isSameMethod_overriddenVarCountAsSameSetter {
    project.create("OverriddenVarCountsAsSameSetter.scala") {"""
      trait A {
        def fo|o_=(x: String): Unit
      }
      abstract class B extends A {
        var f|oo: String
      }
    """} isSame(true)
  }

  @Test
  def isSameMethod_overriddenVarCountAsSameGetter {
    project.create("OverriddenVarCountsAsSameGetter.scala") {"""
      trait A {
        def fo|o: String
      }
      abstract class B extends A {
        var f|oo: String
      }
    """} isSame(true)
  }

  @Test
  def isSameMethod_normalizeSetter {
    project.create("OverriddenVarCountsAsSame.scala") {"""
      trait A {
        def fo|o_=(x: String): Unit
      }
      class B(var foo: String) extends A
      object C {
        val b = new B("test")
        b.fo|o = "setting this"
      }
    """} isSame(true)
  }

  @Test
  def isSameMethod_beCarefulAboutOverriddenSymbols {
    // A bug I had in a previous version would fail
    // this test.
    project.create("BeCarefullAboutOverridden.scala") {"""
      class A {
        def f|oo: String = "hi"
      }
      class B extends A {
        def fo|o(x: String): String = x
        override def foo: String = "bar in B"
      }
    """} isSame(false)
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
    """} isSame(true)
  }

  @Test
  def isSameMethod_canDistinguishOverloadedMethods {
    project.create("CanDistinguishOverloadedMethods.scala") {"""
      class A {
        def ad|d(x: String, y: String) = x + y
        def ad|d(x: Int, y: Int) = x + y
      }
    """} isSame(false)
  }

  @Test def isSameMethod_overloaded {
    val sourceA = project.create("AskOption.scala") {"""
      class AskOption {
        def askO|ption(op: () => String): Option[String] = askOption(op, 10000)
        def askOption(op: () => String, timeout: Int): Option[String] = None
      }
    """}

    val sourceB = project.create("AskOptionUser.scala") {"""
      object AskOptionUser {
        val a = new AskOption
        a.askO|ption { () =>
          "hi there"
        }
      }
    """}

    sourceA.isSameAs(sourceB, true)
  }

  @Test def isSameMethod_overloadedWithExplicitTypeParam {
    // Make sure that we ask the compiler for the right tree
    // in case of overloaded methods where the invocation has
    // explicit type parameters. See
    // SearchPresentationComiler.resolveOverloadedSymbol
    val sourceA = project.create("AskOption.scala") {"""
      class AskOption {
        def askO|ption[A](op: () => A): Option[A] = askOption(op, 10000)
        def askOption[A](op: () => A, timeout: Int): Option[A] = None
      }
    """}

    val sourceB = project.create("AskOptionUser.scala") {"""
      object AskOptionUser {
        val a = new AskOption
        a.askO|ption[String] { () =>
          "hi there"
        }
      }
    """}

    sourceA.isSameAs(sourceB, true)
  }

  @Test
  def isSameMethod_partiallyAppliedMethod {
    project.create("PartiallyApplied.scala") {"""
      trait A {
        def fo|o(x: String): String
        def bar: String => String = fo|o
      }
    """} isSame(true)
  }

  @Test
  def isSameMethod_canDistinguishBetweenLocalMethods {
    project.create("CanDistinguishBetweenLocalVals.scala") {"""
      class A {
        def foo = {
          def |testDef = ""
        }
      }
      class B {
        def foo = {
          def |testDef = ""
        }
      }
    """} isSame(false)
  }

  @Test
  def isSameMethod_canHandleSubclasses {
    project.create("CanHandleSubclasses.scala") {"""
      class A {
        def fo|o(x: String): String = x
      }
      class B extends A {
        def bar = fo|o("test")
      }
    """} isSame(true)
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
    """} isSame(true)
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
    """} isSame(true)
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
    """} isSame(true)
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
    """} isSame(true)
  }

  @Test
  def isSameMethod_casting {
    project.create("Casting.scala") {"""
      class A {
        def fo|o(x: String): String = x
        def bar = (new Object).asInstanceOf[A].fo|o("test")
      }
    """} isSame(true)
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
    """} isSame(true)
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
    """} isSame(true)
  }

  @Test def isSameMethod_selfTypes {
    project.create("Constructors.scala") {"""
      trait A {
        def fo|o(x: String): String = x
      }
      class B { this: A =>
        def bar = fo|o("test")
      }
    """} isSame(true)
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
    """} isSame(true)
  }

  /**----------------------*
   * Vars                  *
   * ----------------------*/

  @Test
  def isSameVar_canCompareVarDefinitionAndGetter {
    project.create("CompareVarDefinitionAndGetter.scala") {"""
      class A {
        var var|iable: String = "test"
        var|iable
      }
    """} isSame(true)
  }

  @Test
  def isSameVar_canCompareVarDefinitionAndSetter {
    project.create("CompareVarDefinitionAndSetter .scala") {"""
      class A {
        var vari|able: String = "test"
        var|iable = "foo"
      }
    """} isSame(true)
  }

  @Test
  def isSameVar_canCompareSetters {
    project.create("CanCompareSetters.scala") {"""
      class A {
        var variable: String = "test"
        var|iable = "foo"
        var|iable = "bar"
      }
    """} isSame(true)
  }

  @Test
  def isSameVar_canCompareGetters {
    project.create("CanCompareGetters.scala") {"""
      object A {
        var variable: String = "test"
      }
      object B {
        A.var|iable
        A.var|iable
      }
    """} isSame(true)
  }

  @Test
  def isSameVar_canCompareExplicitSetterAndDefinition {
    project.create("CanCompareExplicitSetter.scala") {"""
      object A {
        var var|iable: String = "test"
      }
      object B {
        A.variab|le_=("foo")
      }
    """} isSame(true)
  }

  @Test
  def isSameVar_canCompareExplicitSetterAndGetter {
    project.create("CanCompareExplicitSetterAndGetter.scala") {"""
      object A {
        var variable: String = "test"
      }
      object B {
        A.variab|le_=("foo")
        A.variab|le
      }
    """} isSame(true)
  }

  @Test
  def isSameVar_canCompareVarAndOverriddenMethodGetter {
    project.create("CanCompareVarAndOverriddenMethodGetter.scala") {"""
      trait A {
        var fo|o: String
      }
      trait C extends A{
        def f|oo: String
        def foo_=(x: String): Unit
      }"""} isSame(true)
  }

  @Test
  def isSameVar_canCompareVarAndOverriddenMethodSetter {
    project.create("CanCompareVarAndOverriddenMethodSetter.scala") {"""
      trait A {
        var fo|o: String
      }
      trait C extends A{
        def foo: String
        def fo|o_=(x: String): Unit
      }"""} isSame(true)
  }

  @Test
  def isSameVar_canDistinguishBetweenLocalVars {
    project.create("CanDistinguishBetweenLocalVars.scala") {"""
      class A {
        def foo = {
          var |testVar = ""
        }
      }

      class b {
        def foo = {
          var |testVar = ""
        }
      }
    """} isSame(false)
  }

  /**----------------------*
   * Vals                  *
   * ----------------------*/

  @Test
  def isSameVal_canCompareDefinitionAndUsageOfVals = {
    project.create("CanCompareDefinitionAndUsageOfVals.scala") {"""
      class A {
        val va|lue = "test"
        val|ue
      }
    """} isSame(true)
  }

  @Test
  def isSameVal_canCompareGetters = {
    project.create("CanCompareGetters.scala") {"""
      object A {
        val value = "test"
      }
      object B {
        A.val|ue
        A.val|ue
      }
    """} isSame(true)
  }

  @Test
  def isSameVar_canDistinguishBetweenLocalVals {
    project.create("CanDistinguishBetweenLocalVals.scala") {"""
      class A {
        def foo = {
          val |testVal = ""
        }
      }
      class B {
        def foo = {
          val |testVal = ""
        }
      }
    """} isSame(false)
  }

  /**------------------------*
   * possibleNamesOfEntityAt *
   * ------------------------*/

  @Test
  def possibleNamesOfEntityAt_worksForVarDefinition = {
    project.create("PossibleNamesOfEnityAtWorksForGetter.scala") {"""
      class A {
        var variab|le: String = "Test"
      }
    """} expectedNames(List("variable_=","variable"))
  }

  @Test
  def possibleNamesOfEntityAt_worksForSetter = {
    project.create("PossibleNamesOfEnityAtWorksForSetter.scala") {"""
      class A {
        var variab|le: String = "Test"
        var|iable = "Testing"
      }
    """} expectedNames(List("variable_=", "variable"))
  }

  @Test
  def possibleNamesOfEntityAt_worksForApply = {
    project.create("PossibleNamesOfEnityAtWorksForApply.scala") {"""
      object A {
        def apply(x: String) = x
      }
      object Using {
        A.appl|y("test")
      }
    """} expectedNames(List("apply","A"))
  }

  /**----------------------*
   * declarationContaining *
   * ----------------------*/

  @Test
  def declarationContaining_worksForSuperTypes {
    project.create("DeclarationContainingWorksForSuperTypes.scala") {"""
      class Foo
      class B extends |Foo
    """} expectedDeclarationNamed("B")
  }

  @Test
  def declarationContaining_worksForObjectSuperTypes {
    project.create("DeclarationContainingWorksForObjectSuperTypes.scala") {"""
      trait Foo
      object B extends |Foo
    """} expectedDeclarationNamed("B")
  }

  @Test
  def declarationContaining_worksForSelfTypes {
    project.create("DeclarationContainingWorksForSelfTypes.scala") {"""
      trait Foo
      trait B { this: |Foo =>
      }
    """} expectedDeclarationNamed("B")
  }

  @Test
  def declarationContaining_findsTheRightClass {
    // make sure it actually finds the right trait and not just
    // the first trait in the file.
    project.create("DeclarationContainingFindsTheRightClass.scala") {"""
      trait Foo
      trait C { this: foo => }
      trait B { this: |Foo => }
    """} expectedDeclarationNamed("B")
  }

  @Test
  def declarationContaining_IgnoresAnonymousTypes {
    project.create("DeclarationContainingIgnoresAnonymousTypes.scala") {"""
      object Containing {
        trait Foo
        trait Bar
        new Foo with |Bar {}
      }
    """} expectedDeclarationNamed() // Remove when we have #1001818
  }

    /**----------------------*
    * directSupertypes      *
    * ----------------------*/

   @Test
   def directSupertypes_worksForClasses = {
     // The compiler automatically adds Object if not other
     // super-types are defined.
     project.create("DirectSuperTypesWorksForClasses.scala"){"""
       class |A
     """} expectedSupertypes("Object")
   }

   @Test
   def directSupertypes_worksForClasInheritance = {
     // Doesn't add AnyRef when a super-type is provided
     project.create("DirectSuperTypesWorksForClassInheritance.scala"){"""
       class A
       class |B extends A
     """} expectedSupertypes("A")
   }

   @Test
   def worksForTypesNotDefinedInProject = {
     // So we've already tested that it finds Object, but I had some
     // problems where it wouldn't list other types
     project.create("DirectSuperTypesWorksForTypesNotDefinedInProject.scala") {"""
       trait StringOrdering extends |Ordering[String] 
     """} expectedSupertypes("Object", "Comparator", "PartialOrdering", "Serializable")

   }

   @Test
   def directSupertypes_selftypeDoesntCountAsSuperTypeForClasses = {
     // The compiler automatically adds Object if not other
     // super-types are defined.
     project.create("SelftypeDoesntCountAsSuperTypeForClasses.scala"){"""
       trait A
       class |B { this: A => }
     """} expectedSupertypes("Object")
   }

   @Test
   def directSupertypes_worksForTraits = {
     // Object is injected for the super-type of A.
     project.create("DirectSuperTypesWorksForTraits.scala"){"""
       trait |A
     """} expectedSupertypes("Object")
   }

   @Test
   def directSupertypes_worksForMixins = {
     // Compiler doesn't add Object as the super-type of a trait if it
     // extends a class.
     project.create("DirectSuperTypesWorksForMixins.scala"){"""
       class A
       trait B
       trait |C extends A with B
     """} expectedSupertypes("A", "B")
   }

   @Test
   def directSupertypes_worksForMixins2 = {
     // Compiler adds Object as the super-type if it only extends
     // traits.
     project.create("DirectSuperTypesWorksForMixins2.scala"){"""
       trait A
       trait B
       trait |C extends A with B
     """} expectedSupertypes("Object", "A", "B")
   }

   @Test
   def directSupertypes_worksForObjects = {
     // Compiler injects Object as the default super-type of modules
     project.create("DirectSuperTypesWorksForObjects.scala"){"""
       object |A
     """} expectedSupertypes("Object")
   }

   @Test
   def directSupertypes_worksForObjectsWithMixins = {
     // Compiler adds object because A isn't a concrete class.
     project.create("DirectSuperTypesWorksForObjectsWithMixins.scala"){"""
       trait A
       trait B
       object |C extends A with B
     """} expectedSupertypes("Object", "A", "B")
   }

   @Test
   def directSupertypes_selftypeDoesntCountAsSuperTypeForTraits = {
     // Compiler still adds Object as super-type of B
     project.create("SelftypeDoesntCountAsSuperTypeForTraits.scala"){"""
       trait A
       trait |B { this: A => }
     """} expectedSupertypes("Object")
   }

   @Test
   def directSupertypes_worksForMixedClassAndSelftype = {
     // still want to make sure it doesn't list self-type
     project.create("DirectSuperTypesWorksForMixedClassAndSelfType.scala"){"""
       class A
       trait B
       trait |C extends A { this: B => }
     """} expectedSupertypes("A")
   }

   @Test
   def directSupertypes_selftypeWithMixinsShouldStillBeIgnored = {
     // Compiler adds object because A isn't a concrete class.
     project.create("DirectSuperTypesWorksSelftypeWithMixinsShouldStillBeIgnored.scala"){"""
       trait A
       trait B
       trait C
       trait |D extends A { this: B with C => }
     """} expectedSupertypes("Object", "A")
   }

   @Test
   def directSupertypes_worksForIntertanceWithTypeParameters = {
     // Doesn't add AnyRef when a super-type is provided
     project.create("DirectSuperTypesWorksForIntertanceWithTypeParameters.scala"){"""
       trait A[X]
       class |B extends A[String]
     """} expectedSupertypes("Object", "A")
   }

  /**----------------------*
   * Various               *
   * ----------------------*/

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

    sourceA.isSameAs(sourceB)

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

    sourceA.isSameAs(sourceB)

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

    sourceB.isSameAs(sourceA)
    sourceC.isSameAs(sourceA)
    sourceC.isSameAs(sourceB)

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
