package org.scala.tools.eclipse.search.searching

import scala.tools.eclipse.EclipseUserSimulator
import scala.tools.eclipse.ScalaProject
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.eclipse.testsetup.TestProjectSetup
import org.junit._
import org.junit.Assert._
import org.scala.tools.eclipse.search.TestUtil
import scala.tools.eclipse.testsetup.SDTTestUtils
import scala.tools.eclipse.javaelements.ScalaSourceFile

class SearchPresentationCompilerTest {

  import SearchPresentationCompilerTest._
  import SearchPresentationCompiler._

  private final val CaretMarker = '|'

  protected val simulator = new EclipseUserSimulator
  private var project: ScalaProject = _ // A project is needed, otherwise we get NPE when using simulator.

  @Before
  def createProject() {
    project = simulator.createProjectInWorkspace("search-presentation-compiler-test", true)
  }

  @After
  def deleteProject() {
    project.underlying.delete(true, null)
  }

  @Test
  def canGetSymbolAtLocation {
    document {"""
      class ExampleWithSymbol {
        val |x: Int = 42
      }
    """
    } expectedSymbolNamed(Some("x"))
  }

  @Test
  def noneIfAskedForWrongLocation {
    document {"""
      class ExampleWithSymbol {
        val x: Int = 42
      }
      |
    """
    } expectedSymbolNamed(None)
  }

  @Test
  def isSameMethod_withSameSymbol {
    document {"""
      class C {
        def re|ve|rse(x: String) = x.reverse
      }
    """} isSameMethod(true)
  }

  @Test
  def isSameMethod_referenceAndDeclaration {
    document {"""
      class A {
        def fo|o(x: String) = x
        def bar(x: String) = fo|o(x)
      }
    """} isSameMethod(true)
  }

  @Test
  def isSameMethod_falseForDifferentMethods {
    document {"""
      class A {
        def ad|dStrings(x: String, y: String) = x + y
        def ad|dInts(x: Int, y: Int) = x + y
      }
    """} isSameMethod(false)
  }

  @Test
  def isSameMethod_overriddenCountsAsSame {
    document {"""
      class A {
        def fo|o(x: String) = x
      }
      class B extends A {
        override def fo|o(x: String) = x
      }
    """} isSameMethod(true)
  }

  @Test
  def isSameMethod_canDistinguishOverloadedMethods {
    document {"""
      class A {
        def ad|d(x: String, y: String) = x + y
        def ad|d(x: Int, y: Int) = x + y
      }
    """} isSameMethod(false)
  }

  @Test
  def isSameMethod_canHandleRenamedMethods {
    document {"""
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
  def isSameMethod_workWithDifferentProjects {

    val p1 = project("SearchPresentationCompilerTest-workWithDifferentProjects-1") {"""
      class A {
        def foo(x: String) = x.rever|se
      }"""
    }

    val p2 = project("SearchPresentationCompilerTest-workWithDifferentProjects-2") {"""
      class A {
        def foo(x: String) = x.reve|rse
      }"""
    }
    p1 isSameMethod p2

    p1.scalaProject.underlying.delete(true, null)
    p2.scalaProject.underlying.delete(true, null)
  }

  private def scalaSourceFile(code: String): ScalaCompilationUnit = {
    val emptyPkg = simulator.createPackage("")
    simulator.createCompilationUnit(emptyPkg, "A.scala", code.stripMargin).asInstanceOf[ScalaCompilationUnit]
  }

  trait AdhocProject {
    protected val adhocSimulator = new EclipseUserSimulator
    def scalaProject: ScalaProject
    def unit: ScalaCompilationUnit
    def pos: Int
    def isSameMethod(other: AdhocProject): Unit
  }

  private def project(name: String)(text: String) = new AdhocProject {

    var _unit: ScalaCompilationUnit = _
    var _pos: Int = _
    var _scalaProject: ScalaProject = _

    def pos = _pos
    def unit = _unit
    def scalaProject = _scalaProject

    _scalaProject = adhocSimulator.createProjectInWorkspace(name, true)
    _pos = {
        val offset = text.indexOf(CaretMarker)
        if (offset == -1) fail(s"Could not locate caret position marker '${CaretMarker}' in test.")
        offset
    }
    val cleanedText = text.filterNot(_ == CaretMarker).mkString
    val emptyPkg = adhocSimulator.createPackage("")
    _unit = adhocSimulator.createCompilationUnit(emptyPkg, "A.scala", cleanedText.stripMargin).asInstanceOf[ScalaCompilationUnit]

    def isSameMethod(other: AdhocProject): Unit = {
      unit.withSourceFile { (sf1 ,pc1) =>
        val spc = SearchPresentationCompiler(pc1)
        other.unit.withSourceFile { (sf2, pc2) =>
          val spc2 = SearchPresentationCompiler(pc2)
          val isSame = for {
            s1 <- spc.symbolAt(Location(unit, pos), sf1)
            s2 <- spc2.symbolAt(Location(other.unit, other.pos), sf2)
          } yield {
            val imported = spc.importSymbol(spc2)(s2)
            spc.isSameMethod(s1,imported)
          }
          assertEquals(true, isSame.get)
        }(fail("Couldn't get the other source file"))
      }((fail("Couldn't get source file")))
    }
  }

  private def document(text: String) = new {

    def isSameMethod(expected: Boolean): Unit = {
      val pos1 :: pos2 :: Nil = {
        val offset1 = text.indexOf(CaretMarker)
        val offset2 = text.indexOf(CaretMarker, offset1+1)
        if (offset1 == -1 || offset2 == -1) fail(s"Could not locate the two caret position markers '${CaretMarker}' in test.")
        List(offset1,offset2)
      }
      val cleanedText = text.filterNot(_ == CaretMarker).mkString
      val cu = scalaSourceFile(cleanedText)
      cu.withSourceFile { (sf, pc) =>
        val spc = SearchPresentationCompiler(pc)
        val isSame = for {
          s1 <- spc.symbolAt(Location(cu, pos1), sf)
          s2 <- spc.symbolAt(Location(cu, pos2), sf)
        } yield spc.isSameMethod(s1,s2)
        assertEquals(expected, isSame.get)
      }((fail("Couldn't get source file")))
    }

    def expectedSymbolNamed(nameOpt: Option[String]): Unit = {
      val caret: Int = {
        val offset = text.indexOf(CaretMarker)
        if (offset == -1) fail(s"Could not locate caret position marker '${CaretMarker}' in test.")
        offset
      }
      val cleanedText = text.filterNot(_ == CaretMarker).mkString
      val cu = scalaSourceFile(cleanedText)
      cu.withSourceFile { (sf, pc) =>
        val symbol = pc.symbolAt(Location(cu, caret), sf)
        assertEquals(nameOpt, symbol.map(_.nameString))
      } (fail("Couldn't get Scala source file"))
    }
  }

}

object SearchPresentationCompilerTest
  extends TestProjectSetup("SearchPresentationCompilerTest", bundleName= "org.scala.tools.eclipse.search.tests")
     with TestUtil
