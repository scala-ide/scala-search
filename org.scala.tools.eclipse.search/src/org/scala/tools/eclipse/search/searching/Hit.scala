package org.scala.tools.eclipse.search.searching

import scala.tools.eclipse.InteractiveCompilationUnit
import org.eclipse.search.ui.text.Match

sealed abstract class Hit {
  def cu: InteractiveCompilationUnit
  def word: String
  def lineContent: String
  def offset: Int

  def toMatch: Match = new Match(this, offset, word.length)
}
case class PotentialHit(cu: InteractiveCompilationUnit, word: String, lineContent: String, offset: Int) extends Hit
case class ExactHit(cu: InteractiveCompilationUnit, word: String, lineContent: String, offset: Int) extends Hit