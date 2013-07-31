package org.scala.tools.eclipse.search.searching

/**
 * ADT used in results of searches. Simple container that
 * expresses how confident we are that the result is correct.
 *
 * Certain is used when the compiler has been used to validate
 * the result.
 *
 * Uncertain is used when we weren't able to validate the result
 * using the compiler, e.g. there might have been a type-error
 * or similar.
 */
trait Confidence[A] { val value: A }
case class Certain[A](value: A) extends Confidence[A]
case class Uncertain[A](value: A) extends Confidence[A]