package org.scala.tools.eclipse.search.searching

import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.eclipse.EclipseUserSimulator
import org.eclipse.core.runtime.NullProgressMonitor
import org.junit.Assert._
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.core.IClasspathEntry
import scala.Array.canBuildFrom
import org.scala.tools.eclipse.search.indexing.OccurrenceCollector
import org.scala.tools.eclipse.search.indexing.Occurrence
import org.scala.tools.eclipse.search.TypeEntity

trait SourceCreator {

  private final val CaretMarker = '|'

  case class ScalaDocument(unit: ScalaCompilationUnit, markers: Seq[Int]) {

    def expectedSymbolNamed(nameOpt: Option[String]): Unit = {
      unit.withSourceFile { (sf, pc) =>
        val spc = new SearchPresentationCompiler(pc)
        val name = spc.entityAt(Location(unit, markers.head)) map { entity =>
          entity.name
        }
        assertEquals(nameOpt, name)
      } (fail("Couldn't get Scala source file"))
    }

    def expectedNames(names: List[String]): Unit = {
      unit.withSourceFile { (sf, pc) =>
        val spc = new SearchPresentationCompiler(pc)
        val foundnames = spc.possibleNamesOfEntityAt(Location(unit, markers.head))
        assertEquals(names, foundnames.getOrElse(Nil))
      } (fail("Couldn't get Scala source file"))
    }

    def expectedSupertypes(expectedNames: String*): Unit = {
      unit.withSourceFile { (sf, pc) =>
        val spc = new SearchPresentationCompiler(pc)
        val loc = Location(unit, markers.head)
        val enitty = spc.entityAt(loc) flatMap {
          case x: TypeEntity => Some(x)
          case _ => None
        }
        val namesAndComparators = spc.superTypesOf(enitty.get).get
        val names = namesAndComparators.map( _._1 )

        assertEquals(expectedNames, names)
      }(fail("Couldn't get Scala source file"))
    }

    def expectedDeclarationNamed(name: String): Unit = {
      unit.withSourceFile { (sf, pc) =>
        val spc = new SearchPresentationCompiler(pc)
        val occ = spc.declarationContaining(Location(unit, markers.head))
        assertEquals(name, occ.map(_.word).getOrElse(""))
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
    def isSame(expected: Boolean): Unit = {
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

    def isSameAs(other: ScalaDocument, expected: Boolean = true) = {
      val loc1 = Location(unit, markers.head)
      val loc2 = Location(other.unit, other.markers.head)
      unit.withSourceFile { (sf, pc) =>
        val spc = new SearchPresentationCompiler(pc)
        spc.comparator(loc1).map { comparator =>
          comparator.isSameAs(loc2) match {
            case Same => assertEquals(expected, true)
            case _ => assertEquals(expected, false)
          }
        }.getOrElse(fail("Couldn't get comparator for symbol"))
      }((fail("Couldn't get source file")))
    }

    def allOccurrences: Seq[Occurrence] = {
      OccurrenceCollector.findOccurrences(unit).getOrElse(Nil)
    }

    def occurrencesThatMatch(f: Occurrence => Boolean): Seq[Occurrence] = {
      allOccurrences.filter(f)
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

      var count = 0
      var offset = text.indexOf(CaretMarker)
      while (offset != -1) {
        // Subtract the index by the number of carets
        // that preceded this one as they won't show
        // up in the final source.
        cursors = cursors :+ (offset - count)
        offset = text.indexOf(CaretMarker, offset+1)
        count += 1
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