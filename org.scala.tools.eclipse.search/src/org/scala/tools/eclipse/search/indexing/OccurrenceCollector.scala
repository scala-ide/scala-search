package org.scala.tools.eclipse.search.indexing

import scala.tools.eclipse.ScalaPresentationCompiler
import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.logging.HasLogger

/**
 * Used to parse and traverse the parse tree of a compilation unit finding
 * all the occurrence of Scala entities we're interested in.
 */
object OccurrenceCollector extends HasLogger {

  /**
   * Find all occurrences of words we're find interesting in a compilation unit. It
   * will return Left if it wasn't able to access the source file.
   */
  def findOccurrences(file: ScalaSourceFile): Either[String, Seq[Occurrence]] = {
    lazy val err = Left("Couldn't get source file for %".format(file.file.path.toString()))
    file.withSourceFile( (source, pcompiler) => {
      pcompiler.withParseTree(source) { tree =>
        // withParseTree is invariant so we need state the exact type so it doesn't
        // infer the type Right.
        Right(findOccurrences(pcompiler)(file, tree)): Either[String, Seq[Occurrence]]
      }
    })(err)
  }

  private def findOccurrences(pc: ScalaPresentationCompiler)
                             (file: ScalaSourceFile, tree: pc.Tree): Seq[Occurrence] = {
    import pc._

    val occurrences = new scala.collection.mutable.ListBuffer[Occurrence]()
    val traverser = new Traverser {
      override def traverse(t: Tree) {
        t match {

          case Ident(fun) if !isSynthetic(pc)(t, fun.toString) =>
            occurrences += Occurrence(fun.toString, file, t.pos.point, Reference)

          case Select(rest,name) if !isSynthetic(pc)(t, name.toString) =>
            occurrences += Occurrence(name.toString, file, t.pos.point, Reference)
            traverse(rest) // recurse in the case of chained selects: foo.baz.bar

          // Method definitions
          case DefDef(mods, name, _, args, _, body) if !isSynthetic(pc)(t, name.toString) =>
            occurrences += Occurrence(name.toString, file, t.pos.point, Declaration)
            traverseTrees(mods.annotations)
            traverseTreess(args)
            traverse(body)

          // Val's and arguments.
          case ValDef(_, name, tpt, rhs) =>
            occurrences += Occurrence(name.toString, file, t.pos.point, Declaration)
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
    val syntheticNames = Set("<init>")
    tree.pos == pc.NoPosition || syntheticNames.contains(name)
  }

}