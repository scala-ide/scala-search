package org.scala.tools.eclipse.search.indexing

import org.scalaide.core.IScalaProject
import org.scalaide.logging.HasLogger
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import org.eclipse.core.resources.IFile
import org.scala.tools.eclipse.search.SearchPlugin
import org.scala.tools.eclipse.search.Util.scalaSourceFileFromIFile
import java.io.IOException
import org.scalaide.core.compiler.InteractiveCompilationUnit

/**
 * Indexes Scala sources and add all occurrences to the `index`.
 */
class SourceIndexer(val index: Index) extends HasLogger {

  import SourceIndexer._

  /**
   * Indexes all sources in the project `proj`.
   *
   * This removes all previous occurrences recorded in that project.
   *
   * This will fail if during indexing an InvalidPresentationCompilerException
   * or CorruptIndexException is thrown.
   *
   * If any IOExceptions are thrown this method will return a Failure with a
   * reference to all the files that failed. The remaining files will still have
   * been indexed.
   *
   */
  def indexProject(proj: IScalaProject): Try[Unit] = {

    index.deleteIndex(proj.underlying)

    var blockingFailure: Option[Try[Unit]] = None
    var ioFailures: Seq[IFile] = Nil

    val it = proj.allSourceFiles.iterator
    while(blockingFailure.isEmpty && it.hasNext) {
      val file = it.next
      indexIFile(file) match {
        case f@Failure(ex: IOException) => ioFailures = ioFailures :+ file
        case f@Failure(ex) => blockingFailure = Some(f)
        case _ => ()
      }
    }

    blockingFailure.fold {
      if (ioFailures.isEmpty) {
        Success(()): Try[Unit]
      } else {
        Failure(new UnableToIndexFilesException(ioFailures)): Try[Unit]
      }
    }( fail => fail )

  }

  /**
   * Indexes the parse tree of an IFile if the IFile is pointing to a Scala source file.
   *
   * This removes all previous occurrences recorded in that file.
   *
   * This can fail with the following errors
   *
   * IOException
   *   If there is an underlying IOException when trying to open the directory that stores
   *   the index on disc.
   *
   * CorruptIndexException
   *   If the Index somehow has become corrupted.
   *
   * InvalidPresentationCompilerException
   *   If it wasn't able to get the AST of one Scala files that it wanted to Index.
   *
   */
  def indexIFile(file: IFile): Try[Unit] = {
    val success: Try[Unit] = Success(())

    if (index.isIndexable(file)) {
      scalaSourceFileFromIFile(file).fold {
        // TODO: We couldn't convert it to a Scala file for some reason. What to do?
        success
      } {
        cu => indexScalaFile(cu)
      }
    } else {
      success
    }
  }

  /**
   * Indexes the occurrences in a Scala file.
   *
   * This removes all previous occurrences recorded in that file.
   *
   * This can fail with the following errors
   *
   * IOException
   *   If there is an underlying IOException when trying to open the directory that
   *   stores the index on disc.
   *
   * CorruptIndexException
   *   If the Index somehow has become corrupted.
   *
   * InvalidPresentationCompilerException
   *   If it wasn't able to get the AST of one Scala files that it wanted to Index.
   */
  def indexScalaFile(sf: InteractiveCompilationUnit): Try[Unit] = {
    logger.debug(s"Indexing document: ${sf.file.path}")

    for {
      _ <- index.removeOccurrencesFromFile(sf.workspaceFile.getProjectRelativePath(), sf.scalaProject)
      occurrences <- OccurrenceCollector.findOccurrences(sf)
      _ <- index.addOccurrences(occurrences, sf.scalaProject)
    } yield Success(())
  }

}

object SourceIndexer {
  class UnableToIndexFilesException(val files: Seq[IFile]) extends Exception()
}