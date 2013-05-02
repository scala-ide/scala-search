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

    /**
     * Get the symbol of the entity at a given location in a file.
     *
     * The source file needs to be loaded before invoking this method. This can be
     * achieved by invoking `pc.askReload(..)`.
     */
    def symbolAt[A](loc: Location, cu: SourceFile): Option[pc.Symbol] = {
      val typed = new Response[pc.Tree]
      val pos = new OffsetPosition(cu, loc.offset)
      pc.askTypeAt(pos, typed)
      typed.get.fold(
        tree => filterNoSymbols(tree.symbol),
        err => {
          logger.debug(err)
          None
        })
    }

    private def filterNoSymbols(sym: pc.Symbol): Option[pc.Symbol] = {
      if (sym == null || sym == pc.NoSymbol) {
        None
      } else {
        Some(sym)
      }
    }

    /**
     * Check is the symbol `s1` and `s2` are describing the same method. We consider two
     * methods to be the same if
     *
     *  - Both `s1` and `s2` are actual methods
     *  - They have the same name
     *  - s1.owner and s2.owner are in the same hierarchy
     *  - They have the same type signature or one is overriding the other
     */
    def isSameMethod(s1: pc.Symbol, s2: pc.Symbol): Boolean = {
      pc.askOption { () =>

        lazy val isInHiearchy = s1.owner.isSubClass(s2.owner) || s2.owner.isSubClass(s1.owner)
        lazy val hasSameName = s1.nameString == s2.nameString
        lazy val hasSameTypeSignature = s1.typeSignature == s2.typeSignature

        // If s1 and s2 are defined in the same class the owner will be the same
        // thus s1.overriddenSymbol(S2.owner) will actually return s1.
        lazy val isOverridden = s1.owner != s2.owner &&
                                (s1.overriddenSymbol(s2.owner) != pc.NoSymbol ||
                                s2.overriddenSymbol(s1.owner) != pc.NoSymbol)

        s1.isMethod &&
        s2.isMethod &&
        hasSameName &&
        isInHiearchy &&
        (hasSameTypeSignature || isOverridden)

      } getOrElse false
    }

  }
}