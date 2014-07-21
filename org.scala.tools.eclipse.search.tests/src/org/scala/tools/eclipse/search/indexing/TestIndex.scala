package org.scala.tools.eclipse.search.indexing

import org.eclipse.core.runtime.IPath
import scala.util.Try
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.TermQuery
import java.io.File
import org.apache.lucene.search.BooleanClause
import scala.Array.fallbackCanBuildFrom
import scala.Option.option2Iterable
import org.scalaide.core.api.ScalaProject
import org.eclipse.core.runtime.Path
import org.scala.tools.eclipse.search.TestUtil


/**
 * Implementation of Index with adds a few extra methods that makes it
 * easier to test.
 */
trait TestIndex extends Index {

  /**
   * Tries to find all occurrences recorded in a given file, identified by the project
   * relative path and the name of the project in the workspace
   */
  def occurrencesInFile(path: IPath, project: ScalaProject): Try[Seq[Occurrence]] = {
    withSearcher(project) { searcher =>

      val query = new BooleanQuery()
      query.add(new TermQuery(Terms.pathTerm(path)), BooleanClause.Occur.MUST)
      query.add(new TermQuery(Terms.projectTerm(project.underlying)), BooleanClause.Occur.MUST)

      for {
        hit <- searcher.search(query, MAX_POTENTIAL_MATCHES).scoreDocs
        occurrence <- fromDocument(searcher.doc(hit.doc)).right.toOption
      } yield occurrence
    }
  }
}

object TestIndex extends TestUtil {
  def apply(name: String) = new TestIndex {
    override val base = new Path(mkPath("target", "index-test", name))
  }
}