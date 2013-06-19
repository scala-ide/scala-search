package org.scala.tools.eclipse.search
package searching

import org.scala.tools.eclipse.search.indexing.Index
import scala.tools.eclipse.ScalaPlugin
import org.scala.tools.eclipse.search.ErrorReporter
import scala.tools.eclipse.logging.HasLogger
import org.scala.tools.eclipse.search.indexing.SearchFailure
import scala.tools.eclipse.javaelements.ScalaSourceFile
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.NullProgressMonitor

/**
 * Component that provides various methods related to finding Scala entities.
 */
class Finder(index: Index, reporter: ErrorReporter) extends HasLogger {

  private val finder: ProjectFinder = new ProjectFinder 
  /**
   * Find all occurrences of the entity at the given location.
   *
   * - Exact matches are passed to the `hit` function.
   * - Potential matches are passed to the `potentialHit` function. A potential
   *   match is when the index reports and occurrence but we can't type-check
   *   the given point to see if it is an exact match.
   * - Should any errors occur in the Index that we can't handle, the failures
   *   are passed to the `errorHandler` function.
   */
  def occurrencesOfEntityAt(location: Location, monitor: IProgressMonitor)
                           (hit: ExactHit => Unit,
                            potentialHit: PotentialHit=> Unit = _ => (),
                            errorHandler: SearchFailure => Unit = _ => ()): Unit = {

    // Find all the Scala projects that are relevant to search in.
    val enclosingProject = location.cu.scalaProject.underlying
    val all =  finder.projectClosure(enclosingProject)
    val allScala = all.map(ScalaPlugin.plugin.asScalaProject(_)).flatten

    // Get the symbol under the cursor. Use it to find other occurrences.
    location.cu.withSourceFile { (sf, pc) =>
      val spc = new SearchPresentationCompiler(pc)
      for {
        comparator <- spc.comparator(location) onEmpty reporter.reportError(s"Couldn't get comparator based on symbol at ${location.offset} in ${sf.file.path}")
        names <- spc.possibleNamesOfEntityAt(location) onEmpty reporter.reportError(s"Couldn't get name of symbol at ${location.offset} in ${sf.file.path}")
      } {
        val (occurrences, failures) = index.findOccurrences(names, allScala)
        failures.foreach(errorHandler)
        monitor.beginTask("Typechecking for exact matches", occurrences.size)

        val it = occurrences.iterator
        while (it.hasNext && !monitor.isCanceled()) {
          val occurrence = it.next
          monitor.subTask(s"Checking ${occurrence.file.file.name}")
          val loc = Location(occurrence.file, occurrence.offset)
          comparator.isSameAs(loc) match {
            case Same         => hit(ExactHit(occurrence.file, occurrence.word, occurrence.lineContent, occurrence.offset))
            case PossiblySame => potentialHit(PotentialHit(occurrence.file, occurrence.word, occurrence.lineContent, occurrence.offset))
            case NotSame      => logger.debug(s"$occurrence wasn't the same.")
          }
          monitor.worked(1)
        }
        monitor.done()
      }
    }(reporter.reportError(s"Could not access source file ${location.cu.file.path}"))
  }
}
