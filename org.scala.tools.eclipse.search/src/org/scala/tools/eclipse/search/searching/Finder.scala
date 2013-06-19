package org.scala.tools.eclipse.search
package searching

import scala.Option.option2Iterable
import scala.reflect.internal.util.SourceFile
import scala.tools.eclipse.ScalaPlugin
import scala.tools.eclipse.ScalaProject
import scala.tools.eclipse.logging.HasLogger

import org.eclipse.core.runtime.IProgressMonitor
import org.scala.tools.eclipse.search.Entity
import org.scala.tools.eclipse.search.ErrorHandlingOption
import org.scala.tools.eclipse.search.ErrorReporter
import org.scala.tools.eclipse.search.TypeEntity
import org.scala.tools.eclipse.search.indexing.Index
import org.scala.tools.eclipse.search.indexing.Occurrence
import org.scala.tools.eclipse.search.indexing.SearchFailure

/**
 * Component that provides various methods related to finding Scala entities.
 *
 * Instances of this class should not be created manually but rather accessed
 * through the global instance in `SearchPlugin.finder`.
 *
 * To use the API you will want to first get the Scala entity at a given locaiton,
 * like so
 *
 *      val loc = Location(...)
 *      finder.entityAt(loc)
 *
 * The Entity instance can give you some basic information about the entity and should
 * be used in further queries if needed. For more information about Enity, read the
 * associated Scala Doc.
 *
 * Here's an example of how to find all occurrence of an entity:
 *
 *      finder.entityAt(loc) map { entity =>
 *        finder.findSubtypes(findSubtypes, ...)(
 *          handler = hit => hit match {
 *            case Certain(entity) => // ...
 *            case Uncertain(entity) => //...
 *          },
 *          errorHandler = failtures => // ... deal with failures.
 *        )
 *      }
 *
 */
class Finder(index: Index, reporter: ErrorReporter) extends HasLogger {

  private val finder: ProjectFinder = new ProjectFinder

  /**
   * Find the Entity at the given Location. Returns
   * None if it couldn't type-check the given
   * location.
   */
  def entityAt(loc: Location): Option[Entity] = {
    loc.cu.withSourceFile { (sf, pc) =>
      val spc = new SearchPresentationCompiler(pc)
      spc.entityAt(loc)
    }(None)
  }

  /**
   * Find all subtypes of the given `entity`. The handler recieves
   * a Confidence[TypeEntity] for each subtype. See Confidence
   * ScalaDoc for more information.
   *
   * Errors are passed to `errorHandler`.
   */
  def findSubtypes(entity: TypeEntity, monitor: IProgressMonitor)
                  (handler: Confidence[TypeEntity] => Unit,
                   errorHandler: SearchFailure => Unit = _ => ()): Unit = {

    val loc = Location(entity.location.cu, entity.location.offset)

    // Get the declaration that contains the given `hit`.
    def getTypeEntity(hit: Hit): Option[TypeEntity] = {
      hit.cu.withSourceFile { (sf,pc) =>
        val spc = new SearchPresentationCompiler(pc)
        for {
          declaration <- spc.declarationContaining(Location(hit.cu, hit.offset))
          declEntity <- entityAt(Location(declaration.file, declaration.offset))
          typeEntity <- declEntity match {
            case x: TypeEntity => {
              if (spc.isSubtype(entity, x)) Some(x) else {
                logger.debug(s"$x isn't a subtype of $entity")
                None
              }
            }
            case _ => None
          }
        } yield typeEntity
      }(None)
    }

    // Given a hit where the `entity` is used in a super-type position
    // find the declaration that contains it and return it.
    def onHit(hit: Confidence[Hit]): Unit = hit match {
      case Certain(hit)   => getTypeEntity(hit) map Certain.apply foreach handler
      case Uncertain(hit) => getTypeEntity(hit) map Uncertain.apply foreach handler
    }

    entity.location.cu.withSourceFile { (sf, pc) =>
      val spc = new SearchPresentationCompiler(pc)
      for {
        comparator <- spc.comparator(loc) onEmpty reporter.reportError(comparatorErrMsg(loc, sf))
      } {
        val (occurrences, failures) = index.findOccurrencesInSuperPosition(entity.name, relevantProjects(loc))
        failures.foreach(errorHandler)
        monitor.beginTask("Typechecking for exact matches", occurrences.size)
        processSame(occurrences, monitor, comparator, onHit)
        monitor.done()
      }
    }(reporter.reportError(s"Could not access source file ${loc.cu.file.path}"))
  }

  /**
   * Find all occurrences of the given `entity`, that is, references
   * and declarations that match.
   *
   * The handler recieves a Confidence[Hit] for each occurrence. See
   * Confidence ScalaDoc for more information.
   *
   * Errors are passed to `errorHandler`.
   */
  def occurrencesOfEntityAt(entity: Entity, monitor: IProgressMonitor)
                           (handler: Confidence[Hit] => Unit,
                            errorHandler: SearchFailure => Unit = _ => ()): Unit = {

    // Get the symbol under the cursor. Use it to find other occurrences.
    entity.location.cu.withSourceFile { (sf, pc) =>
      val spc = new SearchPresentationCompiler(pc)
      for {
        comparator <- spc.comparator(entity.location) onEmpty reporter.reportError(comparatorErrMsg(entity.location, sf))
        names      <- spc.possibleNamesOfEntityAt(entity.location) onEmpty reporter.reportError(symbolErrMsg(entity.location, sf))
      } {
        val (occurrences, failures) = index.findOccurrences(names, relevantProjects(entity.location))
        failures.foreach(errorHandler)
        monitor.beginTask("Typechecking for exact matches", occurrences.size)
        processSame(occurrences, monitor, comparator, handler)
        monitor.done()
      }
    }(reporter.reportError(s"Could not access source file ${entity.location.cu.file.path}"))
  }

  // Loop through 'occurrences' and use the 'comparator' to find the
  // exact matches. Pass the results along to 'handler'.
  // The 'monitor' is needed to make it possible to cancel it and
  // report progress.
  private def processSame(
      occurrences: Seq[Occurrence],
      monitor: IProgressMonitor,
      comparator: SymbolComparator,
      handler: Confidence[Hit] => Unit): Unit = {
    val it = occurrences.iterator
    while (it.hasNext && !monitor.isCanceled()) {
      val occurrence = it.next
      monitor.subTask(s"Checking ${occurrence.file.file.name}")
      val loc = Location(occurrence.file, occurrence.offset)
      comparator.isSameAs(loc) match {
        case Same         => handler(Certain(occurrence.toHit))
        case PossiblySame => handler(Uncertain(occurrence.toHit))
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
