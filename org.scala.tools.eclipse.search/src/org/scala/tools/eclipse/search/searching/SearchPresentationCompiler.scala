package org.scala.tools.eclipse.search.searching

import scala.tools.eclipse.ScalaPresentationCompiler
import scala.reflect.internal.util.SourceFile
import scala.tools.nsc.interactive.Response
import scala.reflect.internal.util.OffsetPosition
import scala.tools.eclipse.logging.HasLogger

object SearchPresentationCompiler extends HasLogger {

  /**
   * Encapsulates PC logic. Makes it easier to control where the compiler data
   * structures are used and thus make sure that we conform to the synchronization
   * policy of the presentation compiler.
   */
  implicit class SearchPresentationCompiler(val pc: ScalaPresentationCompiler) {

    // ADT used to express the result of symbolAt.
    sealed abstract class SymbolRequest
    case class FoundSymbol(symbol: pc.Symbol) extends SymbolRequest
    case object MissingSymbol extends SymbolRequest
    case object NotTypeable extends SymbolRequest

    /**
     * Get the symbol of the entity at a given location in a file.
     *
     * The source file needs to be loaded before invoking this method. This can be
     * achieved by invoking `pc.askReload(..)`.
     */
    def symbolAt[A](loc: Location, cu: SourceFile): SymbolRequest = {
      val typed = new Response[pc.Tree]
      val pos = new OffsetPosition(cu, loc.offset)
      pc.askTypeAt(pos, typed)
      typed.get.fold(
        tree => filterNoSymbols(tree.symbol),
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
     * TODO: Is the imported symbol discarded again, or does this create a memory leak?
     *       Ticket #1001703
     */
    def importSymbol(spc: SearchPresentationCompiler)(s: spc.pc.Symbol): pc.Symbol = {
      // https://github.com/scala/scala/blob/master/src/reflect/scala/reflect/api/Importers.scala
      val importer0 = pc.mkImporter(spc.pc)
      val importer = importer0.asInstanceOf[pc.Importer { val from: spc.pc.type }]
      importer.importSymbol(s)
    }

    /**
     * Check if symbols `s1` and `s2` are describing the same method. We consider two
     * methods to be the same if
     *
     *  - They have the same name
     *  - s1.owner and s2.owner are in the same hierarchy
     *  - They have the same type signature or one is overriding the other
     */
    def isSameMethod(s1: pc.Symbol, s2: pc.Symbol): Boolean = {
      pc.askOption { () =>

        lazy val isInHiearchy = s1.owner.isSubClass(s2.owner) || s2.owner.isSubClass(s1.owner)

        lazy val hasSameName = {
          def getName(s: pc.Symbol)= {
            if(pc.nme.isLocalName(s.name))
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

      } getOrElse false
    }

  }
}