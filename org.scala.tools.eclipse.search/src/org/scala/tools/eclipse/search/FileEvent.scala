package org.scala.tools.eclipse.search

/**
 * ADT describing file events.
 */
trait FileEvent
case object Added extends FileEvent
case object Changed extends FileEvent
case object Removed extends FileEvent