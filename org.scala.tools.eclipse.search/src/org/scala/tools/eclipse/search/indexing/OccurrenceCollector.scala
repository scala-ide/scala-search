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
                             (file: ScalaSourceFile, t: pc.Tree): Seq[Occurrence] = {
    import pc._

    val occurrences = new scala.collection.mutable.ListBuffer[Occurrence]()
    val traverser = new Traverser {
      override def traverse(tree: Tree) {

        tree match {
          // Direct invocations of methods
          case Apply(t@Ident(fun), args) if !isSynthetic(pc)(t, fun.toString) =>
            occurrences += Occurrence(fun.toString, file, t.pos.point, Reference, Method)
            args.foreach { super.traverse } // recurse on the arguments

          // E.g. foo.bar()
          case Apply(t@Select(rest, name), args) if !isSynthetic(pc)(t, name.toString) =>
            occurrences += Occurrence(name.toString, file, t.pos.point, Reference, Method)
            args.foreach { super.traverse } // recurse on the arguments
            super.traverse(rest) // We recurse in the case of chained invocations, foo.bar().baz()

          // Invoking a method w/o an argument doesn't result in apply, just an Ident node.
          case t@Ident(fun) if !isSynthetic(pc)(t, fun.toString) =>
            occurrences += Occurrence(fun.toString, file, t.pos.point, Reference, Method) /* Not necessarily a method. */

          // Invoking a method on an instance w/o an argument doesn't result in an Apply node, simply a Select node.
          case t@Select(rest,name) if !isSynthetic(pc)(t, name.toString) =>
            occurrences += Occurrence(name.toString, file, t.pos.point, Reference, Method) /* Not necessarily a method. */
            super.traverse(rest) // recurse in the case of chained selects: foo.baz.bar

          // Method definitions
          case t@DefDef(_, name, _, _, _, body) if !isSynthetic(pc)(t, name.toString) =>
            occurrences += Occurrence(name.toString, file, t.pos.point, Declaration, Method)
            super.traverse(body) // We recurse in the case of chained invocations, foo.bar().baz()

          case _ => super.traverse(tree)
        }
      }
    }
    traverser.apply(t)
    occurrences
  }

  private def isSynthetic(pc: ScalaPresentationCompiler)
                         (tree: pc.Tree, name: String): Boolean = {
    val syntheticNames = Set("<init>")
    tree.pos == pc.NoPosition || syntheticNames.contains(name)
  }

}