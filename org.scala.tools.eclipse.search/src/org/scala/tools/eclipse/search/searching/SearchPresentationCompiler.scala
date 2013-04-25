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

  }
}