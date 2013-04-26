package org.scala.tools.eclipse.search.searching

import scala.tools.eclipse.EclipseUserSimulator
import scala.tools.eclipse.ScalaProject
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.eclipse.testsetup.TestProjectSetup
import org.junit._
import org.junit.Assert._
import org.scala.tools.eclipse.search.TestUtil

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

  private def scalaSourceFile(code: String): ScalaCompilationUnit = {
    val emptyPkg = simulator.createPackage("")
    simulator.createCompilationUnit(emptyPkg, "A.scala", code.stripMargin).asInstanceOf[ScalaCompilationUnit]
  }

  private def document(text: String) = new {
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