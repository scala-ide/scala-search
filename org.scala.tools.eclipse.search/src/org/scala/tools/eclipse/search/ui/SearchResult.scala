package org.scala.tools.eclipse.search.ui

import org.eclipse.jface.resource.ImageDescriptor
import org.eclipse.search.ui.ISearchQuery
import org.eclipse.search.ui.text.AbstractTextSearchResult
import org.eclipse.search.ui.text.IEditorMatchAdapter
import org.eclipse.search.ui.text.IFileMatchAdapter
import org.scala.tools.eclipse.search.searching.Hit
import org.eclipse.search.ui.text.Match
import org.scalaide.ui.ScalaImages
import org.scala.tools.eclipse.search.searching.Confidence
import org.scala.tools.eclipse.search.Util
import org.scalaide.core.compiler.InteractiveCompilationUnit
import org.scala.tools.eclipse.search.searching.Certain
import org.scala.tools.eclipse.search.searching.Uncertain

/**
 * Tree used to group search results
 */
trait SearchResultNode
case class ProjectNode(name: String, hits: Int, files: Seq[FileNode]) extends SearchResultNode
case class FileNode(name: String, hits: Int, lines: Seq[LineNode]) extends SearchResultNode
case class LineNode(hit: Confidence[Hit]) extends SearchResultNode


/**
 * Represents the result of executing a search query against Scala
 * files.
 */
class SearchResult(query: ISearchQuery) extends AbstractTextSearchResult {

  /**
   * An implementation of IEditorMatchAdapter appropriate for this search result.
   */
  def getEditorMatchAdapter(): IEditorMatchAdapter = new EditorMatchAdapter

  /**
   * An implementation of IFileMatchAdapter appropriate for this search result.
   */
  def getFileMatchAdapter(): IFileMatchAdapter = new FileMatchAdapter

  /**
   * The image descriptor for the given ISearchResult.
   */
  def getImageDescriptor(): ImageDescriptor = ScalaImages.SCALA_FILE

  /**
   * A user readable label for this search result. The label is typically used in the result view
   * and should contain the search query string and number of matches.
   */
  def getLabel(): String = query.getLabel

  /**
   * The query that produced this search result.
   */
  def getQuery(): ISearchQuery = query

  /**
   * A tooltip to be used when this search result is shown in the UI.
   */
  def getTooltip(): String = query.getLabel

  def projectNodes: Seq[ProjectNode] = {
    // TODO: Cache this, for speed improvements

    type Hits = Seq[Confidence[Hit]]

    def byProjectName(hits: Hits) = hits groupBy { hit =>
      hit.value.cu.scalaProject.underlying.getName
    }

    def byFileName(hits: Hits) = hits groupBy { hit =>
      hit.value.cu.workspaceFile.getName
    }

    def mkLineNodes(hits: Hits): Seq[LineNode] = hits map LineNode.apply

    def mkFileNodes(hits: Hits): Seq[FileNode] = (byFileName(hits) map {
      case ((fileName: String, fileHits: Hits)) =>
          FileNode(fileName, fileHits.size, mkLineNodes(fileHits))
    }).toSeq

    def mkProjectNodes(hits: Hits): Seq[ProjectNode] =
      (byProjectName(hits) map { case ((name: String, projectHits: Hits)) =>
        ProjectNode(name, projectHits.size, mkFileNodes(projectHits))
      }).toSeq

    val res = this.getElements.map(_.asInstanceOf[Confidence[Hit]])

    mkProjectNodes(res)
  }

}