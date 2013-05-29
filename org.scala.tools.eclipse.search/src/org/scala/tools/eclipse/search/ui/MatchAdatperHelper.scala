package org.scala.tools.eclipse.search.ui

import org.scala.tools.eclipse.search.searching.Hit
import org.eclipse.core.resources.IFile
import org.eclipse.search.ui.text.Match
import scala.util.Try

object MatchAdatperHelper {

  def matches(hits: Seq[Hit], file: IFile): Array[Match] = {
    hits.filter(existsAndMatches(_, file)).map(_.toMatch).toArray
  }

  def existsAndMatches(hit: Hit, file: IFile): Boolean = {
    (for {
      hitFile <- getWorkspaceFile(hit)
      if existsAndProjectIsOpen(hitFile) && existsAndProjectIsOpen(file)
    } yield hitFile == file) getOrElse false
  }

  def getWorkspaceFile(hit: Hit): Option[IFile] = {
    // Accessing hit.cu.workspaceFile will throw an
    // exception if hit.cu doesn't exist or the
    // project is closed so we have to check that first.
    if (hit.cu.exists && hit.cu.scalaProject.underlying.isOpen) Option(hit.cu.workspaceFile) else None
  }

  def existsAndProjectIsOpen(file: IFile): Boolean = {
    // if the project is closed, file.exists will also return false
    file != null && file.exists
  }

}