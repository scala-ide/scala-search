package org.scala.tools.eclipse.search.searching

import scala.tools.eclipse.ScalaPresentationCompiler

/**
 * Some extra methods that are convenient for the test code.
 */
class TestSearchPresentationCompiler(pc: ScalaPresentationCompiler) extends SearchPresentationCompiler(pc) {

  def isNoSymbol(loc: Location): Boolean = {
    loc.cu.withSourceFile{ (sf, pc) =>
      symbolAt(loc, sf) match {
        case MissingSymbol => true
        case x => false
      }
    }(false)
  }

  def isTypeError(loc: Location): Boolean = {
    loc.cu.withSourceFile{ (sf, pc) =>
      symbolAt(loc, sf) match {
        case NotTypeable => true
        case x => false
      }
    }(false)
  }

}