package org.scala.tools.eclipse.search.searching

import org.scalaide.core.IScalaProject

/**
 * Represents the scope that should be used when performing a search.
 */
case class Scope(projects: Set[IScalaProject])