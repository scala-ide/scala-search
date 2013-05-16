package org.scala.tools.eclipse.search

import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.eclipse.EclipseUserSimulator
import org.eclipse.core.runtime.NullProgressMonitor
import org.junit.Assert._
import org.scala.tools.eclipse.search.searching.SearchPresentationCompiler._
import org.scala.tools.eclipse.search.searching.Location
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.core.IClasspathEntry

trait SourceCreator {

  private final val CaretMarker = '|'

  case class ScalaDocument(unit: ScalaCompilationUnit, markers: Seq[Int]) {

    def expectedSymbolNamed(nameOpt: Option[String]): Unit = {
      unit.withSourceFile { (sf, pc) =>
        val spc = SearchPresentationCompiler(pc)
        val symbol = spc.symbolAt(Location(unit, markers.head), sf)

        import spc._
        symbol match {
          case FoundSymbol(sym) => assertEquals(nameOpt, Some(sym.nameString))
          case _ => assertEquals(nameOpt, None)
        }
      } (fail("Couldn't get Scala source file"))
    }

    def expectedNoSymbol: Unit = {
      unit.withSourceFile { (sf, pc) =>
        val spc = SearchPresentationCompiler(pc)
        val symbol = spc.symbolAt(Location(unit, markers.head), sf)

        import spc._
        symbol match {
          case MissingSymbol => assertTrue(true)
          case x => fail(s"Expected NoSymbol but found ${x}")
        }
      } (fail("Couldn't get Scala source file"))
    }

    def expectedTypeError: Unit = {
      unit.withSourceFile { (sf, pc) =>
        val spc = SearchPresentationCompiler(pc)
        val symbol = spc.symbolAt(Location(unit, markers.head), sf)

        import spc._
        symbol match {
          case NotTypeable => assertTrue(true)
          case x => fail(s"Expected NotTypeable but found ${x}")
        }
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
        val spc = SearchPresentationCompiler(pc)
        import spc._
        val s1 = spc.symbolAt(Location(unit, markers(0)), sf)
        val s2 = spc.symbolAt(Location(unit, markers(1)), sf)
        val isSame = (s1, s2) match {
          case (FoundSymbol(sym1),FoundSymbol(sym2)) => spc.isSameMethod(sym1,sym2)
          case (_,_) => false
        }
        assertEquals(expected, isSame)
      }((fail("Couldn't get source file")))
    }

    def isSameMethodAs(other: ScalaDocument, expeced: Boolean = true) = {
      unit.withSourceFile { (sf1 ,pc1) =>
        val spc = SearchPresentationCompiler(pc1)
        other.unit.withSourceFile { (sf2, pc2) =>
          val spc2 = SearchPresentationCompiler(pc2)
          val s1 = spc.symbolAt(Location(unit, markers.head), sf1)
          val s2 = spc2.symbolAt(Location(other.unit, other.markers.head), sf2)
          val isSame = (s1,s2) match {
            case (spc.FoundSymbol(sym1),spc2.FoundSymbol(sym2)) => {
              val imported = spc.importSymbol(spc2)(sym2)
              spc.isSameMethod(sym1,imported)
            }
            case (_,_) => false
          }
          assertEquals(expeced, isSame)
        }(fail("Couldn't get the other source file"))
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
    }

    def create(name: String)(text: String): ScalaDocument = {

      var cursors = List[Int]()

      var offset = text.indexOf(CaretMarker)
      while (offset != -1) {
        cursors = cursors :+ offset
        offset = text.indexOf(CaretMarker, offset+1)
      }

      if (cursors.isEmpty)
        fail(s"There has to be atleast one marker '${CaretMarker}' in the source file")

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