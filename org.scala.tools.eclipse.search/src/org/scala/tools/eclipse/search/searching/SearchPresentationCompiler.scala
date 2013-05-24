package org.scala.tools.eclipse.search
package searching

import scala.tools.eclipse.ScalaPresentationCompiler
import scala.reflect.internal.util.SourceFile
import scala.tools.nsc.interactive.Response
import scala.reflect.internal.util.OffsetPosition
import scala.tools.eclipse.logging.HasLogger
import org.scala.tools.eclipse.search.indexing.Occurrence

sealed abstract class ComparisionResult
case object Same extends ComparisionResult
case object NotSame extends ComparisionResult
case object PossiblySame extends ComparisionResult

trait SymbolComparator {
  def isSameAs(loc: Location): ComparisionResult
}

/**
 * Encapsulates PC logic. Makes it easier to control where the compiler data
 * structures are used and thus make sure that we conform to the synchronization
 * policy of the presentation compiler.
 */
class SearchPresentationCompiler(val pc: ScalaPresentationCompiler) extends HasLogger {

  // ADT used to express the result of symbolAt.
  protected sealed abstract class SymbolRequest
  protected case class FoundSymbol(symbol: pc.Symbol) extends SymbolRequest
  protected case object MissingSymbol extends SymbolRequest
  protected case object NotTypeable extends SymbolRequest

  /**
   * Find the name of the symbol at the given location. Returns
   * None if it can't find a valid symbol at the given location.
   */
  def nameOfEntityAt(loc: Location): Option[String] = {
    loc.cu.withSourceFile({ (sf, pc) =>
      symbolAt(loc, sf) match {
        case FoundSymbol(symbol) =>
          pc.askOption(() => symbol.nameString)
        case _ => None
      }
    })(None)
  }

  /**
   * Checks is a symbols is a method or constructor. This is
   * needed such that we can restrict the FindOccurrenceOfMethod
   * to only work for methods until the rest of the entities are
   * suppored.
   */
  def isMethod(loc: Location): Boolean = {
    loc.cu.withSourceFile({ (sf, pc) =>
      symbolAt(loc, sf) match {
        case FoundSymbol(symbol) =>
          pc.askOption { () =>
            symbol.isMethod || symbol.isConstructor
          }.getOrElse(false)
        case _ => false
      }
    })(false)
  }

  /**
   * Get a comparator for a symbol at a given Location. The Comparator can be
   * used to see if symbols at other locations are the same as this symbol.
   */
  def comparator(loc: Location): Option[SymbolComparator] = {
    loc.cu.withSourceFile({ (sf, pc) =>
      symbolAt(loc, sf) match {
        case FoundSymbol(symbol) => Some(new SymbolComparator {
          def isSameAs(otherLoc: Location): ComparisionResult = {
            otherLoc.cu.withSourceFile({ (otherSf, otherPc) =>
              val otherSpc = new SearchPresentationCompiler(otherPc)
              otherSpc.symbolAt(otherLoc, otherSf) match {
                case otherSpc.FoundSymbol(symbol2) =>
                  (for {
                    imported <- importSymbol(otherSpc)(symbol2)
                    isSame   <- isSameMethod(symbol, imported)
                  } yield {
                    if(isSame) Same else NotSame
                  }) getOrElse {
                    NotSame
                  }
                case _ => PossiblySame
              }
            })(PossiblySame)
          }
        })
        case _ => None
      }
    })(None)
  }

  /**
   * Get the symbol of the entity at a given location in a file.
   *
   * The source file needs not be loaded in the presentation compiler prior to
   * this call.
   */
  protected def symbolAt[A](loc: Location, cu: SourceFile): SymbolRequest = {
    val typed = new Response[pc.Tree]
    val pos = new OffsetPosition(cu, loc.offset)
    pc.askTypeAt(pos, typed)
    typed.get.fold(
      tree => {
        filterNoSymbols(tree.symbol)
      },
      err => {
        logger.debug(err)
        NotTypeable
      })
  }

  private def filterNoSymbols(sym: pc.Symbol): SymbolRequest = {
    if (sym == null) {
      MissingSymbol // Symbol is null if there isn't any node at the given location (I think!)
    } else if (sym == pc.NoSymbol) {
      NotTypeable // NoSymbol if we can't typecheck the given node.
    } else {
      FoundSymbol(sym)
    }
  }

  /**
   * Import a symbol from one presentation compiler into another. This is required
   * before you can compare two symbols originating from different presentation
   * compiler instance.
   *
   * @note it should always be called within the Presentation Compiler Thread.
   *
   * TODO: Is the imported symbol discarded again, or does this create a memory leak?
   *       Ticket #1001703
   */
  private def importSymbol(spc: SearchPresentationCompiler)(s: spc.pc.Symbol): Option[pc.Symbol] = {

    // https://github.com/scala/scala/blob/master/src/reflect/scala/reflect/api/Importers.scala
    val importer0 = pc.mkImporter(spc.pc)
    val importer = importer0.asInstanceOf[pc.Importer { val from: spc.pc.type }]

    // Before importing the symbol, we have to make sure that it is fully initialized,
    // otherwise it would access thread unsafe members in spc.pc when importing the symbol
    // into pc.
    spc.pc.askOption { () =>
      s.initialize
      s.ownerChain.foreach(_.initialize)
    } onEmpty (logger.debug("Timed out on initializing symbol before import"))

    pc.askOption { () =>
      importer.importSymbol(s)
    }  onEmpty (logger.debug("Timed out on symbol import"))

  }

  /**
   * Check if symbols `s1` and `s2` are describing the same method. We consider two
   * methods to be the same if
   *
   *  - They have the same name
   *  - s1.owner and s2.owner are in the same hierarchy
   *  - They have the same type signature or one is overriding the other
   */
  private def isSameMethod(s1: pc.Symbol, s2: pc.Symbol): Option[Boolean] = {
    pc.askOption { () =>
      lazy val isInHiearchy = s1.owner.isSubClass(s2.owner) ||
                              s2.owner.isSubClass(s1.owner)

      lazy val hasSameName = {
        def getName(s: pc.Symbol)= {
          if (pc.nme.isSetterName(s.name))
            pc.nme.setterToGetter(s.name.toTermName)
          else if(pc.nme.isLocalName(s.name))
            pc.nme.localToGetter(s.name.toTermName)
          else s.name
        }
        getName(s1) == getName(s2)
      }

      lazy val hasSameTypeSignature = s1.typeSignature =:= s2.typeSignature

      // If s1 and s2 are defined in the same class the owner will be the same
      // thus s1.overriddenSymbol(S2.owner) will actually return s1.
      lazy val isOverridden = s1.owner != s2.owner &&
                             (s1.overriddenSymbol(s2.owner) != pc.NoSymbol ||
                              s2.overriddenSymbol(s1.owner) != pc.NoSymbol)

      (s1 == s2) || // Fast-path.
      hasSameName &&
      isInHiearchy &&
      (hasSameTypeSignature || isOverridden)
    } onEmpty logger.debug("Timed out when comparing symbols")
  }
}
