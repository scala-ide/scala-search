package org.scala.tools.eclipse.search.searching

import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.eclipse.EclipseUserSimulator
import org.eclipse.core.runtime.NullProgressMonitor
import org.junit.Assert._
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.core.IClasspathEntry
import scala.Array.canBuildFrom

trait SourceCreator {

  private final val CaretMarker = '|'

  case class ScalaDocument(unit: ScalaCompilationUnit, markers: Seq[Int]) {

    def expectedSymbolNamed(nameOpt: Option[String]): Unit = {
      unit.withSourceFile { (sf, pc) =>
        val spc = new SearchPresentationCompiler(pc)
        assertEquals(nameOpt, spc.nameOfEntityAt(Location(unit, markers.head)))
      } (fail("Couldn't get Scala source file"))
    }

    def expectedNoSymbol: Unit = {
      unit.withSourceFile { (sf, pc) =>
        val spc = new TestSearchPresentationCompiler(pc)
        assertTrue(spc.isNoSymbol(Location(unit, markers.head)))
      } (fail("Couldn't get Scala source file"))
    }

    def expectedTypeError: Unit = {
      unit.withSourceFile { (sf, pc) =>
        val spc = new TestSearchPresentationCompiler(pc)
        assertTrue(spc.isTypeError(Location(unit, markers.head)))
      } (fail("Couldn't get Scala source file"))
    }

    /**
     * Creates an assertion to see if the symbol under the cursor at the
     * two markers in the file is the same or not.
     *
     * Note: The document has to be created with two markers (i.e. |)
     */
    def isSameMethod(expected: Boolean): Unit = {
      unit.withSourceFile { (sf, pc) =>
        val spc = new SearchPresentationCompiler(pc)
        spc.comparator(Location(unit, markers(0))).map { comparator =>
          comparator.isSameAs(Location(unit, markers(1))) match {
            case Same => assertEquals(true, expected)
            case _ => assertEquals(false, expected)
          }
        }.getOrElse(fail("Couldn't get comparator for symbol"))
      }((fail("Couldn't get source file")))
    }

    def isSameMethodAs(other: ScalaDocument, expected: Boolean = true) = {
      val loc1 = Location(unit, markers.head)
      val loc2 = Location(other.unit, other.markers.head)
      unit.withSourceFile { (sf, pc) =>
        val spc = new SearchPresentationCompiler(pc)
        spc.comparator(loc1).map { comparator =>
          comparator.isSameAs(loc2) match {
            case Same => assertEquals(true, expected)
            case _ => assertEquals(false, expected)
          }
        }.getOrElse(fail("Couldn't get comparator for symbol"))
      }((fail("Couldn't get source file")))
    }

  }

  class Project private (val name: String) {

    import Project._

    private val adhocSimulator = new EclipseUserSimulator
    private var _sources: Seq[ScalaCompilationUnit] = Nil

    val scalaProject = adhocSimulator.createProjectInWorkspace(name, true)

    def source = _sources

    def addProjectsToClasspath(others: Project*): Unit = {
      var entries = new scala.collection.mutable.ArrayBuffer[IClasspathEntry]()

      others.foreach { other =>
        val sourceFolder = other.scalaProject.underlying.getFolder("/src");
        val root = other.scalaProject.javaProject.getPackageFragmentRoot(sourceFolder);
        entries += JavaCore.newSourceEntry(root.getPath());
      }

      val newClasspath = entries ++ scalaProject.javaProject.getRawClasspath()
      scalaProject.javaProject.setRawClasspath(newClasspath.toArray, new NullProgressMonitor)

      val d = scalaProject.underlying.getDescription()
      val refs = scalaProject.underlying.getReferencedProjects() ++ others.map(_.scalaProject.underlying)
      d.setReferencedProjects(refs.toArray)
      scalaProject.underlying.setDescription(d, new NullProgressMonitor)

    }

    def create(name: String)(text: String): ScalaDocument = {

      var cursors = List[Int]()

      var offset = text.indexOf(CaretMarker)
      while (offset != -1) {
        cursors = cursors :+ offset
        offset = text.indexOf(CaretMarker, offset+1)
      }

      val cleanedText = text.filterNot(_ == CaretMarker).mkString
      val emptyPkg = adhocSimulator.createPackage("")
      val unit = adhocSimulator.createCompilationUnit(emptyPkg, name, cleanedText.stripMargin).asInstanceOf[ScalaCompilationUnit]

      unit +: _sources

      ScalaDocument(unit, cursors)
    }

    def delete = {
      scalaProject.underlying.delete(true, new NullProgressMonitor)
    }
  }

  object Project {
    def apply(name: String) = new Project(name)
  }

}