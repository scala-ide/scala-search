package org.scala.tools.eclipse.search.searching

import scala.tools.eclipse.ScalaProject

/**
 * Represents the scope that should be used when performing a search.
 */
case class Scope(projects: Set[ScalaProject])