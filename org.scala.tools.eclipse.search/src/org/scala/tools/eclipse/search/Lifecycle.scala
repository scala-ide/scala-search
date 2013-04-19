package org.scala.tools.eclipse.search

/**
 * Mix into modules that requires start-up and shut-down routines.
 *
 * Modules mixing in this trait should use the "Stackable Traits"
 * patterns. An example usage would be:
 *
 * {{{
 * trait MyModule extends Lifecycle {
 *   abstract override startup() = {
 *     super.startup()
 *     // your own startup routine
 *   }
 *
 *   abstract override shutdown() = {
 *     // your own shutdown routine
 *     stuper.shutdown()
 *   }
 * }
 * }}}
 */
trait Lifecycle {

  def startup(): Unit = ()
  def shutdown(): Unit = ()

}