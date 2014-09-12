package org.scala.tools.eclipse.search.searching

import org.scalaide.core.compiler.IScalaPresentationCompiler

/**
 * Some extra methods that are convenient for the test code.
 */
class TestSearchPresentationCompiler(pc: IScalaPresentationCompiler) extends SearchPresentationCompiler(pc) {

  def isNoSymbol(loc: Location): Boolean = {
    symbolAt(loc) match {
      case MissingSymbol => true
      case x => false
    }
  }

  def isTypeError(loc: Location): Boolean = {
    symbolAt(loc) match {
      case NotTypeable => true
      case x => false
    }
  }

}