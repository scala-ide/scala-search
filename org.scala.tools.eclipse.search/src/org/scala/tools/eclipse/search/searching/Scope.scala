package org.scala.tools.eclipse.search.searching

import org.scalaide.core.api.ScalaProject

/**
 * Represents the scope that should be used when performing a search.
 */
case class Scope(projects: Set[ScalaProject])