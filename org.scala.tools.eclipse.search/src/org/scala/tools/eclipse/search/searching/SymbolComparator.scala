package org.scala.tools.eclipse.search.searching

sealed abstract class ComparisionResult
case object Same extends ComparisionResult
case object NotSame extends ComparisionResult
case object PossiblySame extends ComparisionResult

trait SymbolComparator {
  def isSameAs(loc: Location): ComparisionResult
}

object SymbolComparator {
  def apply(f: Location => ComparisionResult): SymbolComparator = {
    new SymbolComparator {
      override def isSameAs(loc: Location) = f(loc)
    }
  }
}
