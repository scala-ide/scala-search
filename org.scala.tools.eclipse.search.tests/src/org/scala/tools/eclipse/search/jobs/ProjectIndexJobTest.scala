package org.scala.tools.eclipse.search.jobs

import java.io.IOException
import java.util.concurrent.CountDownLatch
import scala.tools.eclipse.ScalaProject
import scala.util.Failure
import scala.util.Success
import org.apache.lucene.index.CorruptIndexException
import org.eclipse.core.resources.IFile
import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.Path
import org.eclipse.core.runtime.jobs.IJobChangeEvent
import org.junit.Test
import org.mockito.Matchers
import org.mockito.Mockito.atLeast
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.when
import org.scala.tools.eclipse.search.Added
import org.scala.tools.eclipse.search.Changed
import org.scala.tools.eclipse.search.FileEvent
import org.scala.tools.eclipse.search.JobChangeAdapter
import org.scala.tools.eclipse.search.Removed
import org.scala.tools.eclipse.search.TestUtil
import org.scala.tools.eclipse.search.indexing.Index
import org.scala.tools.eclipse.search.indexing.OccurrenceCollector
import org.scala.tools.eclipse.search.searching.SourceCreator
import ProjectIndexJobTest.Project
import org.scala.tools.eclipse.search.indexing.SourceIndexer

class ProjectIndexJobTest {

  import ProjectIndexJobTest._

  @Test def whenStartingItShouldTriggerIndexing() {
    // When the job is started it should start indexing the existing
    // files in the project.
    val project = Project("WhenStartingItShouldTriggerIndexing")
    val latch = new CountDownLatch(1)

    val indexer = mockedSuccessfullIndexerConfigReIndexing(project.scalaProject)

    testWithIndexer(indexer, project.scalaProject, latch) { () =>
      verify(indexer, atLeast(1)).indexProject(project.scalaProject)
    }
  }

  @Test def invalidPCExceptionWhenIndexing() {
    // When indexing a project fails with an InvalidPresentationCompilerException we
    // don't want it to try and index the project again.
    val project = Project("InvalidPCExceptionWhenIndexing")
    val latch = new CountDownLatch(1)

    val indexer = mockIndexerConfigWithException(
        project.scalaProject,
        new OccurrenceCollector.InvalidPresentationCompilerException(""))

    testWithIndexer(indexer, project.scalaProject, latch) { () =>
      verify(indexer, times(1)).indexProject(project.scalaProject)
    }
  }

  @Test def ioExceptionWhenIndexing() {
    // When indexing a project fails with an IOException we expect it to
    // try and index the project again later.
    val project = Project("IOExceptionWhenIndexing")
    val latch = new CountDownLatch(2)
    val config = mockIndexerConfigWithException(project.scalaProject, new IOException)

    testWithIndexer(config, project.scalaProject, latch) { () =>
      verify(config, atLeast(2)).indexProject(project.scalaProject)
    }
  }

  @Test def corrupIndexExceptionWhenIndexing() {
    // When indexing a project fails with an CorruptIndexException we expect it to
    // try and index the project again later.
    val project = Project("CorrupIndexExceptionWhenIndexing")
    val latch = new CountDownLatch(2)
    val config = mockIndexerConfigWithException(project.scalaProject, new CorruptIndexException(""))

    testWithIndexer(config, project.scalaProject, latch) { () =>
      verify(config, atLeast(2)).indexProject(project.scalaProject)
    }
  }

  @Test def itIndexesTheChangeset() {
    // When the ProjectIndexJob is given a changeset we expect it to
    // handle the changes properly.
    val latch = new CountDownLatch(1)
    val name = "ItIndexesTheChangeset"
    val project = Project(name)

    val added = project.create("Added.scala")("class Added")
    val changed = project.create("Changed.scala")("class Added")
    val deleted = project.create("Deleted.scala")("class Deleted")

    val changes = List(
        (added.unit.workspaceFile, Added),
        (changed.unit.workspaceFile, Changed),
        (deleted.unit.workspaceFile, Removed)
    )

    val config = mockedSuccessfullIndexerConfigNoReindexing(project.scalaProject)

    testWithIndexer(config, project.scalaProject, latch, changeset = changes) { () =>
      verify(config, atLeast(1)).indexIFile(added.unit.workspaceFile)
      verify(config, atLeast(1)).indexIFile(changed.unit.workspaceFile)
      verify(config.index, atLeast(1)).removeOccurrencesFromFile(deleted.unit.workspaceFile.getProjectRelativePath, project.scalaProject)
    }
  }

}

object ProjectIndexJobTest
  extends SourceCreator
     with TestUtil {

  val INTERVAL = 500

  class TestIndex extends Index {
    override val base: IPath = new Path(mkPath("target","project-index-job-test"))
  }

  def testWithIndexer(indexer: SourceIndexer,
                      project: ScalaProject,
                      latch: CountDownLatch,
                      changeset: Seq[(IFile, FileEvent)] = Nil)
                     (f: () => Unit): Unit = {
    val job = ProjectIndexJob(indexer, project, changeset)
    job.addJobChangeListener(new JobChangeAdapter {
      override def done(event: IJobChangeEvent): Unit = latch.countDown()
    })
    job.schedule()
    latch.await(EVENT_DELAY, java.util.concurrent.TimeUnit.SECONDS)
    f()
  }

  def mockedSuccessfullIndexerConfig(project: ScalaProject) = {
    val index = mock(classOf[TestIndex])
    when(index.isIndexable(Matchers.argThat(mocks.args.anyInstance[IFile]))).thenReturn(true)
    when(index.removeOccurrencesFromFile(
        Matchers.argThat(mocks.args.anyInstance[IPath]),
        Matchers.argThat(mocks.args.anyInstance[ScalaProject]))).thenReturn(Success(()))

    val indexer = mock(classOf[SourceIndexer])
    when(indexer.index).thenReturn(index)
    when(indexer.indexProject(project)).thenReturn(Success(()))
    when(indexer.indexIFile(Matchers.argThat(mocks.args.anyInstance[IFile]))).thenReturn(Success(()))
    indexer
  }

  def mockedSuccessfullIndexerConfigReIndexing(project: ScalaProject) = {
    val indexer = mockedSuccessfullIndexerConfig(project)
    when(indexer.index.indexExists(project.underlying)).thenReturn(false)
    indexer
  }

  def mockedSuccessfullIndexerConfigNoReindexing(project: ScalaProject) = {
    val indexer = mockedSuccessfullIndexerConfig(project)
    when(indexer.index.indexExists(project.underlying)).thenReturn(true)
    indexer
  }

  def mockIndexerConfigWithException(project: ScalaProject, ex: Exception) = {
    val index = mock(classOf[TestIndex])
    when(index.deleteIndex(project.underlying)).thenReturn(Success(true))
    when(index.indexExists(project.underlying)).thenReturn(false)

    val indexer = mock(classOf[SourceIndexer])
    when(indexer.index).thenReturn(index)
    when(indexer.indexProject(project)).thenReturn(Failure(ex))
    indexer
  }
}