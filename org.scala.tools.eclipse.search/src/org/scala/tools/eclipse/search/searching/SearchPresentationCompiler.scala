package org.scala.tools.eclipse.search
package searching

import scala.reflect.internal.util.OffsetPosition
import scala.reflect.internal.util.RangePosition
import scala.reflect.internal.util.SourceFile
import scala.tools.eclipse.ScalaPresentationCompiler
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.eclipse.logging.HasLogger
import scala.tools.nsc.interactive.Response

import org.scala.tools.eclipse.search.Entity
import org.scala.tools.eclipse.search.ErrorHandlingOption
import org.scala.tools.eclipse.search.TypeEntity
import org.scala.tools.eclipse.search.indexing.Declaration
import org.scala.tools.eclipse.search.indexing.Occurrence

sealed trait CompilerProblem
case object UnexpectedNoSymbol extends CompilerProblem
case object UnknownCompilerProblem extends CompilerProblem
case object CantLoadFile extends CompilerProblem

/**
 * Encapsulates PC logic. Makes it easier to control where the compiler data
 * structures are used and thus make sure that we conform to the synchronization
 * policy of the presentation compiler.
 */
class SearchPresentationCompiler(val pc: ScalaPresentationCompiler) extends HasLogger {

  // ADT used to express the result of symbolAt.
  protected sealed abstract class SymbolRequest {
    // In case you're only interested in the FoundSymbol
    def toOption: Option[pc.Symbol] = None
  }
  protected case class FoundSymbol(symbol: pc.Symbol) extends SymbolRequest {
    override def toOption: Option[pc.Symbol] = Some(symbol)
  }
  protected case object MissingSymbol extends SymbolRequest
  protected case object NotTypeable extends SymbolRequest
  protected case object Unexpected extends SymbolRequest

  /**
   * Given a `Location` return a Entity representation of
   * whatever Scala entity is being used or declared at the
   * given position.
   */
  def entityAt(loc: Location): Either[CompilerProblem, Option[Entity]] = {
    for {
      symbolOpt <- (symbolAt(loc) match {
        case FoundSymbol(symbol) => Right(Some(symbol))
        case MissingSymbol | NotTypeable => Right(None)
        case Unexpected => Left(UnexpectedNoSymbol): Either[CompilerProblem, Option[pc.Symbol]]
      }).right
      entityOpt <- symbolOpt match {
        case Some(symbol) => (pc.askOption { () => Right(Some(symbolToEntity(symbol))) }
                                 getOrElse Left(UnknownCompilerProblem)).right
        case None => Right(None).right
      }
    } yield entityOpt
  }

  /**
   * Given a symbol, create the proper instance of Entity that can safely be
   * used by clients.
   *
   * Note: Needs to be invoked on the presentation compiler thread
   */
  private def symbolToEntity(symbol: pc.Symbol): Entity = {

    val loc = for {
      file <- Util.scalaSourceFileFromAbstractFile(symbol.sourceFile) onEmpty
              logger.debug(s"Cound't add location to the entity as it wasn't possible " +
                            "to get a ScalaSourceFile form symbol.sourceFile")
      loc  <- pc.locate(symbol, file) onEmpty
              logger.debug(s"Cound't add location to the entity as it wasn't possible " +
                            "to get the exact location of the symbol.")
      (_, offset) = loc

    } yield Location(file, offset)

    val nme = getName(symbol)

    val typeName = {
      val full = symbol.tpe.toString
      full.split('.').lastOption.getOrElse(full)
    }

    trait EntityImpl { this: Entity =>
      protected final val sym = symbol // Have to do this to avoid compiler bug.
      override def name = nme
      override def location = loc
      override def isReference(loc: Location) = createSymbolComparator(sym).isSameAs(loc)
      override def alternativeNames = possibleNames(sym).getOrElse(List(name))
    }

    trait TypeEntityImpl extends EntityImpl { this: TypeEntity =>
      override def displayName = typeName
      override def supertypes = directSupertypes(sym)
    }

    // Make sure the symbol is initialized
    symbol.initialize // Implementation checks if if it's already initialized

    if (symbol.isJavaInterface) new Interface with TypeEntityImpl
    else if (symbol.isTrait) new Trait with TypeEntityImpl
    else if (symbol.isClass) new Class with TypeEntityImpl { override val isAbstract = sym.isAbstractClass }
    else if (symbol.isModule) new Module with TypeEntityImpl { override def displayName = name }
    else if (symbol.isType) new Type with TypeEntityImpl
    else if (symbol.isMethod) new Method with EntityImpl
    else if (symbol.isVal) new Val with EntityImpl
    else if (symbol.isVar) new Var with EntityImpl
    else new UnknownEntity with EntityImpl
  }

  /**
   * In case the symbol is a var/val where the name can be either the
   * local name, the setter or the getter the getter name will
   * be used.
   *
   * Note: Needs to be invoked on the PC thread
   */
  private def getName(symbol: pc.Symbol): String = {
    if(pc.nme.isSetterName(symbol.name)) {
      pc.nme.setterToGetter(symbol.name.toTermName).decodedName.toString
    } else if (pc.nme.isLocalName(symbol.name)) {
      pc.nme.localToGetter(symbol.name.toTermName).decodedName.toString
    } else {
      symbol.decodedName
    }
  }

  /**
   * Scala supports a variety of different syntaxes for referencing the
   * same entity. This will return a list containing all the valid names
   * of a given entity.
   *
   * For example Foo.apply() and Foo() are both valid names for an invocation
   * of Foo.apply
   */
  private def possibleNames(symbol: pc.Symbol): Option[List[String]] = {

    def namesForValOrVars(symbol: pc.Symbol) = {
      val (setterName, getterName) = {
        if (pc.nme.isSetterName(symbol.name)) {
          (symbol.decodedName, pc.nme.setterToGetter(symbol.name.toTermName).decodedName)
        } else {
          val getter = if(pc.nme.isLocalName(symbol.name)) pc.nme.localToGetter(symbol.name.toTermName)
                       else symbol.name
          (pc.nme.getterToSetter(getter.toTermName).decodedName, getter.decodedName)
        }
      }

      List(setterName.toString, getterName.toString)
    }

    def namesForApply(symbol: pc.Symbol) = {
      List(symbol.decodedName.toString, symbol.owner.decodedName.toString)
    }

    pc.askOption { () =>
      if (isValOrVar(symbol)) namesForValOrVars(symbol)
      else if (symbol.nameString == "apply") namesForApply(symbol)
      else List(symbol.decodedName.toString)
    }
  }

  /**
   * Given a symbol return the TypeEntity for each of the direct super
   * types.
   */
  private def directSupertypes(symbol: pc.Symbol): Seq[TypeEntity] = {

    def isRealSymbol(s: pc.Symbol) = s != null && s != pc.NoSymbol

    pc.askOption { () =>
      for {
        parent  <- symbol.parentSymbols if isRealSymbol(parent)
        entity  =  symbolToEntity(parent)
        typed   <- Some(entity) collect { case t: TypeEntity => t }
      } yield typed
    } getOrElse Nil
  }

  /**
   * Given a location, find the declaration that contains the
   * given location and return the declared entity.
   *
   *   class A extends Foo with Bar
   *
   * If the location is Foo it will return A
   *
   * Note: The InteractiveCompilationUnit referenced by `loc`
   *       needs to be part of the ScalaProject that is managed
   *       by the presentation compiler used to instantiate this
   *       instance of SearchPresentationCompiler
   */
  def declarationContaining(loc: Location): Either[CompilerProblem, Option[Entity]] = {

    loc.cu.withSourceFile { (sf,locPc) =>

      import locPc._

      class ModuleDefOrClassDefLocator(pos: Position) extends Locator(pos) {
        override def isEligible(t: Tree) =
          (t.isInstanceOf[ModuleDef] || t.isInstanceOf[ClassDef]) && super.isEligible(t)
      }

      locPc.withParseTree(sf) { parsed =>
        val pos = new OffsetPosition(sf, loc.offset)
        new ModuleDefOrClassDefLocator(pos).locateIn(parsed) match {
          case t: ModuleDef => entityAt(Location(loc.cu, t.pos.point))
          case t @ ClassDef(_, name, _ , _) if name.startsWith("$anon") => Right(None) // Ignoring anonymous types for now, till we have #1001818
          case t: ClassDef => entityAt(Location(loc.cu, t.pos.point))
          case _ => Right(None)
        }
      }
    }(Left(CantLoadFile))
  }

  /**
   * Comparator that can be used to compare symbols.
   */
  private def createSymbolComparator(symbol: pc.Symbol) = SymbolComparator { otherLoc =>

    def compare(s1: pc.Symbol, s2: pc.Symbol): Option[Boolean] = pc.askOption { () =>
      if (s1.isLocal || s2.isLocal) isSameSymbol(s1,s2)
      else if (isValOrVar(s1)) isSameValOrVar(s1, s2)
      else if (s1.isMethod) isSameMethod(s1.asMethod, s2)
      else isSameSymbol(s1, s2)
    } flatten

    otherLoc.cu.withSourceFile { (_, otherPc) =>
      val otherSpc = new SearchPresentationCompiler(otherPc)
      otherSpc.symbolAt(otherLoc) match {
        case otherSpc.FoundSymbol(symbol2) => (for {
          imported <- importSymbol(otherSpc)(symbol2)
          isSame   <- compare(symbol,imported)
          result   = if(isSame) Same else NotSame
        } yield result) getOrElse NotSame
        case _ => PossiblySame
      }
    }(PossiblySame)
  }

  /**
   * Get the symbol of the entity at a given location
   *
   * The source file needs not be loaded in the presentation compiler prior to
   * this call.
   *
   * This resolves overloaded method symbols. @See resolveOverloadedSymbol for
   * more information.
   */
  protected def symbolAt[A](loc: Location): SymbolRequest = {
    loc.cu.withSourceFile { (sf, _) =>
      val typed = new Response[pc.Tree]
      val pos = new OffsetPosition(sf, loc.offset)
      pc.askTypeAt(pos, typed)
      typed.get.fold(
        tree => {
          if (tree == pc.EmptyTree) MissingSymbol else {
            (for {
              symbol <- Option(tree.symbol)
            } yield (for {
              (overloaded, treePos) <- pc.askOption { () => (symbol.isOverloaded, tree.pos) } if overloaded
            } yield resolveOverloadedSymbol(treePos, sf))
                    getOrElse filterNoSymbols(symbol)) getOrElse Unexpected
          }
        },
        err => {
          logger.debug(err)
          NotTypeable
        })
    }(NotTypeable)
  }

  /**
   * If the symbol at the given location is known to be an overloaded symbol
   * this will resolve the overloaded symbol by asking the compiler to type-check
   * a bit more of the tree at the given location.
   *
   * This is needed because `askTypeAt` will only return the smallest fully attributed
   * tree that encloses position. Consider the following example
   *
   *   def askOption[A](op: () => A): Option[A] = askOption(op, 10000)
   *   def askOption[A](op: () => A, timeout: Int): Option[A] = None
   *
   *   askOption { () =>
   *     ...
   *   }
   *
   *  If the position the beginning of the `askOption` identifier (the invocation) then
   *  the presentation compiler will only type-check the receiver (the Select node) and
   *  not the arguments and thus will not resolve the overloaded method symbol.
   *
   *  We fix this by asking the compiler to type-check more of the tree using a
   *  RangePosition.
   *
   */
  private def resolveOverloadedSymbol[A](treePos: pc.Position, cu: SourceFile): SymbolRequest = {

    // There are two cases we need to consider then figuring out how
    // far the RangePosition should reach.
    //
    // 1. It is a normal Apply.
    //
    //    In this case we simply extend the RangePosition 1 char past
    //    the identifier, i.e.
    //
    //      {foo(}x)
    //
    //    where {} represents the RangePosition.
    //
    // 2. It's a TypeApply, i.e. a parametric method where the type
    //    parameters have been supplied explicitly.
    //
    //    In this case we have to extend the RangePosition 2 chars
    //    past the end of the last type parameter,i.e.
    //
    //      {foo[String,Int](}
    //

    val EXTRA_SPACE = 1
    val TYPE_PARAMS_END_CHAR = 1

    val posInCaseOfTypeApply = pc.withParseTree(cu) { parsed =>
      import pc._
      // We want to find the TypeApply that contains the Select
      // node.
      class TypeApplyLocator(pos: Position) extends Locator(pos) {
        override def isEligible(t: Tree) = t.isInstanceOf[TypeApply] && super.isEligible(t)
      }

      new TypeApplyLocator(treePos.pos).locateIn(parsed) match {
        case TypeApply(_, types) => {
          val max = types.map(_.pos.endOrPoint).max
          Some((treePos.pos.point, max + EXTRA_SPACE + TYPE_PARAMS_END_CHAR))
        }
        case x => None
      }
    }

    lazy val posInCaseOfApply = (
        treePos.pos.point,
        treePos.pos.endOrPoint + EXTRA_SPACE)

    val (start, end) = posInCaseOfTypeApply.getOrElse(posInCaseOfApply)

    val typed = new Response[pc.Tree]
    val pos = new RangePosition(cu, start, start, end)
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
      def fullyInitialize(thisSym: spc.pc.Symbol): Unit = {
        spc.pc.definitions.fullyInitializeSymbol(thisSym)
        thisSym.sourceModule.initialize
      }
      (s :: s.ownerChain) foreach (fullyInitialize)
    } onEmpty (logger.debug("Timed out on initializing symbol before import"))

    pc.askOption { () =>
      importer.importSymbol(s)
    }  onEmpty (logger.debug("Timed out on symbol import"))

  }

  /**
   * Check if `s2` is valid reference to the symbol var/val `s1`.
   *
   * Given that we merge overridden members we need to consider
   * that a def may also be a valid reference to a var/val since a
   * val can override a method and a method can override an abstract
   * var.
   *
   * @note it should always be called within the Presentation Compiler Thread.
   */
  private def isSameValOrVar(s1: pc.Symbol, s2: pc.Symbol): Option[Boolean] = {
    /*
     * S1: Is the getter/setter or the underlying symbol of a var/val.
     * S2: Same as S1 or a method.
     */
    if (isValOrVar(s2)) {
      isSameSymbol(s1.getter, s2.getter)
    } else if (s2.isMethod) {
      if (pc.nme.isSetterName(s2.name)) isSameSymbol(s1.setter, s2)
      else isSameSymbol(s1.getter, s2)
    } else None
  }

  /**
   * Check if `s2` is a valid reference to the method `s1`.
   *
   * Given that we merge overridden members we need to consider
   *
   *   1. A 0-arg method can be overridden by a val
   *   2. A method can override an abstract var (both getter and setter)
   *
   * So S2 can either a method, a val/var or their underlying symbols.
   *
   * @note it should always be called within the Presentation Compiler Thread.
   *
   */
  private def isSameMethod(s1: pc.MethodSymbol, s2: pc.Symbol): Option[Boolean] = {
    // S1: Is a method, and not a getter/setter of a field.
    // S2: Can be a method, a val/var or their underlying symbols
    if (isValOrVar(s2)) {
      if (pc.nme.isSetterName(s1.name)) isSameSymbol(s1, s2.setter)
      else isSameSymbol(s1, s2.getter)
    } else if (s2.isMethod) {
      isSameSymbol(s1, s2)
    } else None
  }

  /**
   * Check if symbols `s1` and `s2` are describing the same symbol. We consider two
   * symbols to be the same if
   *
   *  - They have the same name
   *  - s1.owner and s2.owner are in the same hierarchy
   *  - They have the same type signature or one is overriding the other
   *
   * Now if the symbols have the same name and are in the same hierarchy but the
   * type-signature doesn't match we check if one symbol is overriding the other.
   * The reason for this is that the signature might contain references to this.type.
   *
   * In the case of comparing vals or vars the caller of this method has to make
   * sure the the appropriate symbols are compared, e.g. that both S1 and S2
   * are setters.
   */
  private def isSameSymbol(s1: pc.Symbol, s2: pc.Symbol): Option[Boolean] = {
    pc.askOption { () =>

      lazy val isInHiearchy = s1.owner.isSubClass(s2.owner) ||
                              s2.owner.isSubClass(s1.owner)

      lazy val hasSameName = s1.name == s2.name

      lazy val hasSameTypeSignature = s1.typeSignature =:= s2.typeSignature

      // If s1 and s2 are defined in the same class the owner will be the same
      // thus s1.overriddenSymbol(S2.owner) will actually return s1.
      lazy val isOverridden = s1.owner != s2.owner &&
                             (s1.overriddenSymbol(s2.owner) == s2 ||
                              s2.overriddenSymbol(s1.owner) == s1)

      (s1 == s2) || // Fast-path.
      hasSameName &&
      isInHiearchy &&
      (hasSameTypeSignature || isOverridden)
    } onEmpty logger.debug("Timed out when comparing symbols")
  }

  private def isValOrVar(s: pc.Symbol): Boolean = {
    s.isVar || s.isVal || s.isSetter || s.isGetter
  }
}
