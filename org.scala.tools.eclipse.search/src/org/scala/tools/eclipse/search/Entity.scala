package org.scala.tools.eclipse.search

import org.scala.tools.eclipse.search.searching.Location
import org.scala.tools.eclipse.search.searching.ComparisionResult

/**
 * Our own simple abstraction for Scala entities. This abstraction is
 * used to pass information between the compiler and the components
 * that need it.
 */
sealed trait Entity {
  def name: String

  /**
   * The position where the entity was found. This is an option as
   * we might now have to location if Entity was loaded from a library
   * that doesn't have sources attached.
   */
  def location: Option[Location]

  /**
   * Checks if a given location is a reference to this entity
   */
  def isReference(loc: Location): ComparisionResult

  /**
   * Scala supports a variety of different syntaxes for referencing the
   * same entity. This will return a list containing all the valid names
   * of a given entity.
   *
   * For example Foo.apply() and Foo() are both valid names for an invocation
   * of Foo.apply
   */
  def alternativeNames: Seq[String]

  /**
   * Used to check if the entity at the given entity is something we
   * can find occurrences of. This is useful until we support all kinds
   * of entities.
   */
  def canFindReferences: Boolean = {
    this match {
      case _: Val | _: Var | _: Method => true
      case _ => false
    }
  }

  override def toString = s"${this.getClass}($name)"

}

sealed trait TypeEntity extends Entity {

  def displayName: String

  def supertypes: Seq[TypeEntity]

}

trait NameExtractor[A <: Entity] {
  def unapply(v: A): Option[String] = Some(v.name)
}

trait NameAndDisplayNameExtractor[A <: TypeEntity] {
  def unapply(v: A) = Some((v.name, v.displayName))
}

trait Val extends Entity
object Val extends NameExtractor[Val]

trait Var extends Entity
object Var extends NameExtractor[Var]

trait Method extends Entity
object Method extends NameExtractor[Method]

trait Type extends TypeEntity
object Type extends NameAndDisplayNameExtractor[Type]

trait Trait extends TypeEntity
object Trait extends NameAndDisplayNameExtractor[Trait]

trait Class extends TypeEntity {
  def isAbstract: Boolean
}
object Class extends NameAndDisplayNameExtractor[Class]

trait Module extends TypeEntity
object Module extends NameAndDisplayNameExtractor[Module]

// A Java Interface
trait Interface extends TypeEntity
object Interface extends NameAndDisplayNameExtractor[Interface]

// Used in the few cases where we can't tell what kind of entity we've got.
trait UnknownEntity extends Entity
object UnknownEntity extends NameExtractor[UnknownEntity]