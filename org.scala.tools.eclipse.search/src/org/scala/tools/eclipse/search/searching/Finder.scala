package org.scala.tools.eclipse.search
package searching

import scala.Option.option2Iterable
import scala.reflect.internal.util.SourceFile
import org.scalaide.core.IScalaPlugin
import org.scalaide.core.IScalaProject
import org.scalaide.logging.HasLogger

import org.eclipse.core.runtime.IProgressMonitor
import org.scala.tools.eclipse.search.Entity
import org.scala.tools.eclipse.search.ErrorHandlingOption
import org.scala.tools.eclipse.search.ErrorReporter
import org.scala.tools.eclipse.search.TypeEntity
import org.scala.tools.eclipse.search.indexing.Index
import org.scala.tools.eclipse.search.indexing.Occurrence
import org.scala.tools.eclipse.search.indexing.SearchFailure
import scala.collection.mutable

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

  /**
   * Find the Entity at the given Location.
   */
  def entityAt(loc: Location): Either[CompilerProblem, Option[Entity]] = {
    loc.cu.withSourceFile { (sf, pc) =>
      val spc = new SearchPresentationCompiler(pc)
      spc.entityAt(loc)
    } getOrElse (Left(CantLoadFile))
  }

  /**
   * Find all subtypes of the given `entity`. The handler recieves
   * a Confidence[TypeEntity] for each subtype. See Confidence
   * ScalaDoc for more information.
   *
   * Errors are passed to `errorHandler`.
   */
  def findSubtypes(entity: TypeEntity, scope: Scope, monitor: IProgressMonitor)
                  (handler: Confidence[TypeEntity] => Unit,
                   errorHandler: SearchFailure => Unit = _ => ()): Unit = {

    /*
     *  A short description of how we find the sub-types
     *
     *  Step 1: Based on the name of the `entity` we look for all
     *          the locations where the entity is mentioned in the
     *          declaration of another type.
     *  Step 2: For each declaration we found from the index, filter
     *          out the occurrences that are hits of the `entity`, i.e.
     *          use the compiler to take care of ambiguities as we always do
     *  Step 3: For each of these occurrence, get the TypeEntity of the
     *          enclosing declaration
     */

    // Just this to make sure a sub-type isn't reported twice.
    // I.e. when finding subtypes of Foo it won't list Bar twice in
    // the following example: `trait Bar extends Foo[Foo[String]]`
    // see test 'FinderTest.findAllSubclasses_worksWithNestedTypeConstructors'
    val alreadyReportedNames = new mutable.ListBuffer[String]

    // Get the declaration that contains the given `hit`.
    def getTypeEntity(hit: Hit): Option[TypeEntity] = {
      hit.cu.withSourceFile { (sf,pc) =>
        val spc = new SearchPresentationCompiler(pc)
        val maybeEntity = spc.declarationContaining(Location(hit.cu, hit.offset)).right.toOption.flatten
        //TODO: Report error
        maybeEntity match {
          case Some(x: TypeEntity) if x.name != entity.name && !alreadyReportedNames.contains(x.name) =>
            alreadyReportedNames.append(x.name)
            x
          case None => null
        }
      }
    }

    // Given a hit where the `entity` is used in a super-type position
    // find the declaration that contains it and return it.
    def onHit(hit: Confidence[Hit]): Unit = hit match {
      case Certain(hit)   => getTypeEntity(hit) map Certain.apply foreach handler
      case Uncertain(hit) => getTypeEntity(hit) map Uncertain.apply foreach handler
    }

    val (occurrences, failures) = index.findOccurrencesInSuperPosition(entity.name, scope)
    failures.foreach(errorHandler)
    monitor.beginTask("Typechecking for exact matches", occurrences.size)
    processSame(entity, occurrences, monitor, onHit)
    monitor.done()
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
  def occurrencesOfEntityAt(entity: Entity, scope: Scope, monitor: IProgressMonitor)
                           (handler: Confidence[Hit] => Unit,
                            errorHandler: SearchFailure => Unit = _ => ()): Unit = {

    val names = entity.alternativeNames
    val (occurrences, failures) = index.findOccurrences(names, scope)
    failures.foreach(errorHandler)
    monitor.beginTask("Typechecking for exact matches", occurrences.size)
    processSame(entity, occurrences, monitor, handler)
    monitor.done()
  }

  // Loop through 'occurrences' and use the 'comparator' to find the
  // exact matches. Pass the results along to 'handler'.
  // The 'monitor' is needed to make it possible to cancel it and
  // report progress.
  private def processSame(
      entity: Entity,
      occurrences: Seq[Occurrence],
      monitor: IProgressMonitor,
      handler: Confidence[Hit] => Unit): Unit = {

    for { occurrence <- occurrences if !monitor.isCanceled } {
      monitor.subTask(s"Checking ${occurrence.file.file.name}")
      val loc = Location(occurrence.file, occurrence.offset)
      entity.isReference(loc) match {
        case Same         => handler(Certain(occurrence.toHit))
        case PossiblySame => handler(Uncertain(occurrence.toHit))
        case NotSame      => logger.debug(s"$occurrence wasn't the same.")
      }
      monitor.worked(1)
    }
  }

}
