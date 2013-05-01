package org.scala.tools.eclipse.search.searching

import org.eclipse.core.resources.IProject
import scala.collection.mutable.Stack

trait ProjectFinder {

  /**
   * Given a project, return a Set of all the projects that this project
   * references and all the projects that references this project (transitively).
   * That is, the transitive closure.
   *
   * The original project is included in the Set.
   *
   * If the project is closed this will return the empty set.
   */
  def projectClosure(project: IProject): Set[IProject] = {
    if (project.isOpen) {
      var all = (project +: refs(project)).toSet

      // Projects = Undirected Acyclic Graph -> DF Traversal
      var missing: Stack[IProject] = Stack( all.toSeq :_* )
      while (!missing.isEmpty) {
        val p = missing.pop
        val news = refs(p).filter(!all.contains(_))
        missing.pushAll(news)
        all = all ++ news.toSet
      }

      all
    } else Set()
  }

  private def refs(project: IProject): Seq[IProject] = {
    val referenced  = project.getReferencedProjects
    val referencing = project.getReferencingProjects
    (referenced ++ referencing)
  }

}