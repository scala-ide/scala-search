package org.scala.tools.eclipse.search.indexing

import org.eclipse.core.runtime.IPath
import org.eclipse.core.resources.IProject
import scala.util.Try
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.TermQuery
import java.io.File
import org.apache.lucene.search.BooleanClause
import scala.Array.fallbackCanBuildFrom
import scala.Option.option2Iterable


/**
 * Implementation of Index with adds a few extra methods that makes it
 * easier to test.
 */
class TestIndex(indicesRoot: File) extends Index(indicesRoot) {

  /**
   * Tries to find all occurrences recorded in a given file, identified by the project
   * relative path and the name of the project in the workspace
   */
  def occurrencesInFile(path: IPath, project: IProject): Try[Seq[Occurrence]] = {
    withSearcher(project) { searcher =>

      val query = new BooleanQuery()
      query.add(new TermQuery(Terms.pathTerm(path)), BooleanClause.Occur.MUST)
      query.add(new TermQuery(Terms.projectTerm(project)), BooleanClause.Occur.MUST)

      for {
        hit <- searcher.search(query, MAX_POTENTIAL_MATCHES).scoreDocs
        occurrence <- fromDocument(searcher.doc(hit.doc)).right.toOption
      } yield occurrence
    }
  }

}