package org.scala.tools.eclipse.search
package searching

import org.scala.tools.eclipse.search.indexing.Index
import scala.tools.eclipse.ScalaPlugin
import org.scala.tools.eclipse.search.ErrorReporter
import scala.tools.eclipse.logging.HasLogger
import org.scala.tools.eclipse.search.indexing.SearchFailure

/**
 * Component that provides various methods related to finding Scala entities.
 */
trait Finder extends ProjectFinder
                with ErrorReporter
                with HasLogger {

  this: Index =>

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
  def occurrencesOfEntityAt(location: Location)(hit: Result => Unit,
                                                potentialHit: Result=> Unit = _ => (),
                                                errorHandler: SearchFailure => Unit = _ => ()): Unit = {

    // Find all the Scala projects that are relevant to search in.
    val enclosingProject = location.cu.scalaProject.underlying
    val all =  projectClosure(enclosingProject)
    val allScala = all.map(ScalaPlugin.plugin.asScalaProject(_)).flatten

    // Get the symbol under the cursor. Use it to find other occurrences.
    location.cu.withSourceFile { (sf, pc) =>
      val spc = new SearchPresentationCompiler(pc)
      for {
        comparator <- spc.comparator(location) onEmpty reportError(s"Couldn't get comparator based on symbol at ${location.offset} in ${sf.file.path}")
        name <- spc.nameOfEntityAt(location) onEmpty reportError(s"Couldn't get name of symbol at ${location.offset} in ${sf.file.path}")
      } {
        val (occurrences, failures) = findOccurrences(name, allScala)
        logger.debug(s"Found ${occurrences.size} potential matches")
        failures.foreach(errorHandler)
        occurrences.foreach { occurrence =>
          val loc = Location(occurrence.file, occurrence.offset)
          comparator.isSameAs(loc) match {
            case Same => hit(occurrence.toResult)
            case PossiblySame => potentialHit(occurrence.toResult)
            case NotSame => logger.debug(s"$occurrence wasn't the same.")
          }
        }
      }
    }(reportError(s"Could not access source file ${location.cu.file.path}"))
  }
}
