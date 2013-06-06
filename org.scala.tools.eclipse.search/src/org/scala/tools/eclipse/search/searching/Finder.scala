package org.scala.tools.eclipse.search
package searching

import org.scala.tools.eclipse.search.indexing.Index
import scala.tools.eclipse.ScalaPlugin
import org.scala.tools.eclipse.search.ErrorReporter
import scala.tools.eclipse.logging.HasLogger
import org.scala.tools.eclipse.search.indexing.SearchFailure
import scala.tools.eclipse.javaelements.ScalaSourceFile
import org.eclipse.core.runtime.IProgressMonitor
import scala.tools.eclipse.ScalaProject
import org.scala.tools.eclipse.search.indexing.Occurrence
import scala.reflect.internal.util.SourceFile

/**
 * Component that provides various methods related to finding Scala entities.
 */
class Finder(index: Index, reporter: ErrorReporter) extends HasLogger {

  private val finder: ProjectFinder = new ProjectFinder

  /**
   * Find all subclasses of the type at the given location.
   *
   * - Exact matches are passed to the `hit` function.
   * - Potential matches are passed to the `potentialHit` function. A potential
   *   match is when the index reports and occurrence but we can't type-check
   *   the given point to see if it is an exact match.
   * - Should any errors occur in the Index that we can't handle, the failures
   *   are passed to the `errorHandler` function.
   */
  def findAllSubclasses(location: Location, monitor: IProgressMonitor)
                       (hit: ExactHit => Unit,
                        potentialHit: PotentialHit => Unit = _ => (),
                        errorHandler: SearchFailure => Unit = _ => ()): Unit = {

    def getDeclaration(hit: Hit, spc: SearchPresentationCompiler) =
      spc.declarationContaining(Location(hit.cu, hit.offset))

    location.cu.withSourceFile { (sf, pc) =>
      val spc = new SearchPresentationCompiler(pc)
      for {
        comparator <- spc.comparator(location) onEmpty reporter.reportError(comparatorErrMsg(location, sf))
        name       <- spc.nameOfEntityAt(location) onEmpty reporter.reportError(symbolErrMsg(location, sf))
      } {
        val (occurrences, failures) = index.findOccurrencesInSuperPosition(name, relevantProjects(location))
        failures.foreach(errorHandler)
        monitor.beginTask("Typechecking for exact matches", occurrences.size)
        processSame(
            occurrences,
            monitor,
            comparator,
            getDeclaration(_, spc) foreach { x => hit(x.toExactHit) },
            getDeclaration(_, spc) foreach { x => potentialHit(x.toPotentialHit) })
        monitor.done()
      }
    }(reporter.reportError(s"Could not access source file ${location.cu.file.path}"))

  }

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
                            potentialHit: PotentialHit => Unit = _ => (),
                            errorHandler: SearchFailure => Unit = _ => ()): Unit = {

    // Get the symbol under the cursor. Use it to find other occurrences.
    location.cu.withSourceFile { (sf, pc) =>
      val spc = new SearchPresentationCompiler(pc)
      for {
        comparator <- spc.comparator(location) onEmpty reporter.reportError(comparatorErrMsg(location, sf))
        names      <- spc.possibleNamesOfEntityAt(location) onEmpty reporter.reportError(symbolErrMsg(location, sf))
      } {
        val (occurrences, failures) = index.findOccurrences(names, relevantProjects(location))
        failures.foreach(errorHandler)
        monitor.beginTask("Typechecking for exact matches", occurrences.size)
        processSame(occurrences, monitor, comparator, hit, potentialHit)
        monitor.done()
      }
    }(reporter.reportError(s"Could not access source file ${location.cu.file.path}"))
  }

  // Loop through 'occurrences' and use the 'comparator' to find the
  // exact matches. Pass the results along to 'hit' or 'potentialHit'
  // depending on the result.
  // The 'monitor' is needed to make it possible to cancel it and
  // report progress.
  private def processSame(
      occurrences: Seq[Occurrence],
      monitor: IProgressMonitor,
      comparator: SymbolComparator,
      hit: ExactHit => Unit,
      potentialHit: PotentialHit => Unit = _ => ()): Unit = {
    val it = occurrences.iterator
    while (it.hasNext && !monitor.isCanceled()) {
      val occurrence = it.next
      monitor.subTask(s"Checking ${occurrence.file.file.name}")
      val loc = Location(occurrence.file, occurrence.offset)
      comparator.isSameAs(loc) match {
        case Same         => hit(occurrence.toExactHit)
        case PossiblySame => potentialHit(occurrence.toPotentialHit)
        case NotSame      => logger.debug(s"$occurrence wasn't the same.")
      }
      monitor.worked(1)
    }
  }

  private def relevantProjects(loc: Location): Set[ScalaProject] = {
    val enclosingProject = loc.cu.scalaProject.underlying
    val all =  finder.projectClosure(enclosingProject)
    all.map(ScalaPlugin.plugin.asScalaProject(_)).flatten
  }

  private def comparatorErrMsg(location: Location, sf: SourceFile) =
    s"Couldn't get comparator based on symbol at ${location.offset} in ${sf.file.path}"

  private def symbolErrMsg(location: Location, sf: SourceFile) =
    s"Couldn't get name of symbol at ${location.offset} in ${sf.file.path}"
}
