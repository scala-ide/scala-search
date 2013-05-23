package org.scala.tools.eclipse.search.searching

import scala.tools.eclipse.InteractiveCompilationUnit
import org.eclipse.search.ui.text.Match

/**
 * Represents a successful search result.
 */
case class Result(cu: InteractiveCompilationUnit, word: String, lineContent: String, offset: Int) {
  def toMatch: Match = new Match(this, offset, word.length)
}