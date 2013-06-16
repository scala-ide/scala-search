package org.scala.tools.eclipse.search.indexing

import scala.tools.eclipse.ScalaPresentationCompiler
import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.logging.HasLogger
import scala.util._

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
  def findOccurrences(file: ScalaSourceFile): Try[Seq[Occurrence]] = {

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

  private def findOccurrences(pc: ScalaPresentationCompiler)
                             (file: ScalaSourceFile, tree: pc.Tree): Seq[Occurrence] = {
    import pc._

    val occurrences = new scala.collection.mutable.ListBuffer[Occurrence]()
    val traverser = new Traverser {
      override def traverse(t: Tree) {
        t match {

          case Ident(fun) if !isSynthetic(pc)(t, fun.toString) =>
            occurrences += Occurrence(fun.toString, file, t.pos.point, Reference, t.pos.lineContent)

          case Select(rest,name) if !isSynthetic(pc)(t, name.toString) =>
            occurrences += Occurrence(name.toString, file, t.pos.point, Reference, t.pos.lineContent)
            traverse(rest) // recurse in the case of chained selects: foo.baz.bar

          // Method definitions
          case DefDef(mods, name, _, args, _, body) if !isSynthetic(pc)(t, name.toString) =>
            occurrences += Occurrence(name.toString, file, t.pos.point, Declaration, t.pos.lineContent)
            traverseTrees(mods.annotations)
            traverseTreess(args)
            traverse(body)

          // Val's and arguments.
          case ValDef(_, name, tpt, rhs) =>
            occurrences += Occurrence(name.toString, file, t.pos.point, Declaration, t.pos.lineContent)
            traverse(tpt)
            traverse(rhs)

          case _ =>
            super.traverse(t)
        }
      }
    }
    traverser.apply(tree)
    occurrences.toList
  }

  private def isSynthetic(pc: ScalaPresentationCompiler)
                         (tree: pc.Tree, name: String): Boolean = {
    tree.pos == pc.NoPosition
  }

}