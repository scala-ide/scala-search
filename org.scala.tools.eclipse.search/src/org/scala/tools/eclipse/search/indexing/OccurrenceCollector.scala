package org.scala.tools.eclipse.search.indexing

import scala.tools.eclipse.ScalaPresentationCompiler
import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.logging.HasLogger
import scala.util._
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.reflect.internal.util.Position
import scala.reflect.internal.util.SourceFile
import scala.reflect.internal.util.NoSourceFile
import scala.reflect.internal.Chars

/**
 * Used to parse and traverse the parse tree of a compilation unit finding
 * all the occurrence of Scala entities we're interested in.
 */
object OccurrenceCollector extends HasLogger {

  class InvalidPresentationCompilerException(msg: String) extends Exception(msg)

  /**
   * Find all occurrences of words we're find interesting in a compilation unit.
   *
   * This can fail in the following ways
   *
   * InvalidPresentationCompilerException:
   *   if the presentation compiler is not available (for instance, if it cannot
   *   be started because of classpath issues)
   *
   */
  def findOccurrences(file: ScalaCompilationUnit): Try[Seq[Occurrence]] = {

    lazy val failedWithSF: Try[Seq[Occurrence]] = Failure(
        new InvalidPresentationCompilerException(
            s"Couldn't get source file for ${file.workspaceFile.getProjectRelativePath()}"))

    lazy val noFileError: Try[Seq[Occurrence]] = Failure(
        new InvalidPresentationCompilerException(
            s"Couldn't get source file for ${file.file.path}"))

    if (file.exists) {
      file.withSourceFile( (source, pcompiler) => {
        pcompiler.withParseTree(source) { tree =>
          Success(findOccurrences(pcompiler)(file, tree)): Try[Seq[Occurrence]]
        }
      })(failedWithSF)
    } else noFileError
  }

  // TODO : remove once we have the fix to SI-8205 (Scala PR
  // 3427)
  @deprecated("This should be removed after PR 3247 is released", since="0.2.1")
  private def altLineContent(p: Position): String = {
    val (l,s) = (p.line, p.source)
      if (l == 0 || p.source == NoSourceFile) "" else {
        s.content drop s.lineToOffset(l-1) takeWhile (c => !Chars.isLineBreakChar(c.toChar)) mkString ""
      }
  }


  private def findOccurrences(pc: ScalaPresentationCompiler)
                             (file: ScalaCompilationUnit, tree: pc.Tree): Seq[Occurrence] = {
    import pc._

    val occurrences = new scala.collection.mutable.ListBuffer[Occurrence]()

    val traverser = new Traverser {
      private var isSuper = false
      override def traverse(t: Tree) {

        // Avoid passing the same arguments all over.
        def mkOccurrence = Occurrence(_: String, file, t.pos.point, _: OccurrenceKind, altLineContent(t.pos), isSuper)

        t match {

          case Ident(name) if !isSynthetic(pc)(t) =>
            occurrences += mkOccurrence(name.decodedName.toString, Reference)

          case Select(rest,name) if !isSynthetic(pc)(t) =>
            occurrences += mkOccurrence(name.decodedName.toString, Reference)
            traverse(rest) // recurse in the case of chained selects: foo.baz.bar

          // Method definitions
          case DefDef(mods, name, _, args, _, body) if !isSynthetic(pc)(t) =>
            occurrences += mkOccurrence(name.decodedName.toString, Declaration)
            traverseTrees(mods.annotations)
            traverseTreess(args)
            traverse(body)

          // Val's and arguments.
          case ValDef(_, name, tpt, rhs) =>
            occurrences += mkOccurrence(name.decodedName.toString, Declaration)
            traverse(tpt)
            traverse(rhs)

          // Class and Trait definitions
          case ClassDef(_, name, _, Template(supers, ValDef(_,_,selfType,_), body)) =>
            occurrences += mkOccurrence(name.decodedName.toString, Declaration)
            isSuper = true
            traverseTrees(supers)
            isSuper = false
            traverse(selfType)
            traverseTrees(body)

          // Object definition
          case ModuleDef(_, name, Template(supers, _, body)) =>
            occurrences += mkOccurrence(name.decodedName.toString, Declaration)
            isSuper = true
            traverseTrees(supers)
            isSuper = false
            traverseTrees(body)

          // Make sure that type arguments aren't listed as being in 'super-type' position
          case AppliedTypeTree(tpe, args) =>
            traverse(tpe)
            val oldIsSuper = isSuper
            isSuper = false
            traverseTrees(args)
            isSuper = oldIsSuper

          case _ =>
            super.traverse(t)
        }
      }
    }
    traverser.apply(tree)
    occurrences.toList
  }

  private def isSynthetic(pc: ScalaPresentationCompiler)
                         (tree: pc.Tree): Boolean = {
    tree.pos == pc.NoPosition
  }

}
