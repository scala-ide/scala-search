package org.scala.tools.eclipse.search.jobs

import java.io.IOException
import org.scalaide.core.testsetup.SDTTestUtils
import org.scalaide.core.testsetup.SDTTestUtils.waitUntil
import org.scalaide.core.testsetup.TestProjectSetup
import scala.util.Failure
import scala.util.Success
import org.apache.lucene.index.CorruptIndexException
import org.eclipse.core.resources.IFile
import org.eclipse.core.runtime.IPath
import org.junit.Test
import org.mockito.Matchers
import org.mockito.Mockito.atLeast
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.when
import org.scala.tools.eclipse.search.indexing.Index
import org.scala.tools.eclipse.search.indexing.OccurrenceCollector
import org.scala.tools.eclipse.search.indexing.SourceIndexer
import java.util.concurrent.CountDownLatch
import org.eclipse.core.runtime.jobs.IJobChangeListener
import org.eclipse.core.runtime.jobs.IJobChangeEvent
import ProjectIndexJobTest.mocks
import org.scala.tools.eclipse.search.TestUtil
import org.scala.tools.eclipse.search.indexing.SourceIndexer
import org.eclipse.core.runtime.Path
import org.scala.tools.eclipse.search.JobChangeAdapter
import org.scalaide.core.IScalaProject

class ProjectIndexJobTest {

  import ProjectIndexJobTest._

  @Test def whenStartingItShouldTriggerIndexing() {
    // When the job is started it should start indexing the existing
    // files in the project.

    val latch = new CountDownLatch(1)

    val config = mockedSuccessfullIndexerConfigReIndexing
    when(config.indexProject(project)).thenReturn(Success())

    val job = ProjectIndexJob(config, project, INTERVAL)
    job.addJobChangeListener(new JobChangeAdapter {
      override def done(event: IJobChangeEvent): Unit = latch.countDown()
    })

    job.schedule()
    latch.await(EVENT_DELAY, java.util.concurrent.TimeUnit.SECONDS)

    verify(config, atLeast(1)).indexProject(project)
  }

  @Test def changingFilesShouldTriggerIndexing() {
    // When a file is changed SourceIndexer.indexScalaFile should be invoke
    val latch = new CountDownLatch(2)
    val arg = mocks.args.fileNamed("Change.scala")

    val config = mockedSuccessfullIndexerConfigNoReindexing
    when(config.indexIFile(Matchers.argThat(arg))).thenReturn(Success())

    val job = ProjectIndexJob(config, project, INTERVAL)
    job.addJobChangeListener(new JobChangeAdapter {
      override def done(event: IJobChangeEvent): Unit = latch.countDown()
    })

    addSourceFile(project)("Change.scala", "")
    addContent(project)("Change.scala", "class Foo")

    job.schedule()
    latch.await(EVENT_DELAY, java.util.concurrent.TimeUnit.SECONDS)

    verify(config, times(2)).indexIFile(Matchers.argThat(arg))
  }

  @Test def addingAFileShouldTriggerIndexing() {
    // When a file is added SourceIndexer.indexScalaFile should be invoke
    val latch = new CountDownLatch(1)
    val arg = mocks.args.fileNamed("Add.scala")

    val config = mockedSuccessfullIndexerConfigNoReindexing

    val job = ProjectIndexJob(config, project, INTERVAL)
    job.addJobChangeListener(new JobChangeAdapter {
      override def done(event: IJobChangeEvent): Unit = latch.countDown()
    })

    addSourceFile(project)("Add.scala", "")

    job.schedule()
    latch.await(EVENT_DELAY, java.util.concurrent.TimeUnit.SECONDS)

    verify(config, times(1)).indexIFile(Matchers.argThat(arg))
  }

  @Test def deletingAFileShouldTriggerRemoval() {
    // When a file is deleted Index.removeOccurrencesFromFile should be invoked

    val latch = new CountDownLatch(1)
    val arg = mocks.args.pathEndingWith("Remove.scala")

    val config = mockedSuccessfullIndexerConfigNoReindexing

    when(config.index.removeOccurrencesFromFile(
        Matchers.argThat(arg),
        Matchers.eq(project))).thenReturn(Success())

    val job = ProjectIndexJob(config, project, INTERVAL)
    job.addJobChangeListener(new JobChangeAdapter {
      override def done(event: IJobChangeEvent): Unit = latch.countDown()
    })

    addSourceFile(project)("Remove.scala", "")
    deleteSourceFile(project)("Remove.scala")

    job.schedule()
    latch.await(EVENT_DELAY, java.util.concurrent.TimeUnit.SECONDS)

    verify(config.index).removeOccurrencesFromFile(
        Matchers.argThat(arg),
        Matchers.eq(project))
  }


  @Test def invalidPCExceptionWhenIndexing() {
    // When indexing a project fails with an InvalidPresentationCompilerException we
    // don't want it to try and index the project again.
    val latch = new CountDownLatch(1)

    val config = mockIndexerConfigWithException(new OccurrenceCollector.InvalidPresentationCompilerException(""))
    when(config.indexProject(project)).thenReturn(
        Failure(new OccurrenceCollector.InvalidPresentationCompilerException("")))

    val job = ProjectIndexJob(config, project, INTERVAL)
    job.addJobChangeListener(new JobChangeAdapter {
      override def done(event: IJobChangeEvent): Unit = latch.countDown()
    })

    job.schedule()
    latch.await(EVENT_DELAY, java.util.concurrent.TimeUnit.SECONDS)

    verify(config, times(1)).indexProject(project)
  }

  @Test def ioExceptionWhenIndexing() {
    // When indexing a project fails with an IOException we expect it to
    // try and index the project again later.
    val latch = new CountDownLatch(2)

    val config = mockIndexerConfigWithException(new IOException)

    when(config.indexProject(project)).thenReturn(
        Failure(new IOException))

    val job = ProjectIndexJob(config, project, INTERVAL)
    job.addJobChangeListener(new JobChangeAdapter {
      override def done(event: IJobChangeEvent): Unit = latch.countDown()
    })

    job.schedule()
    latch.await(EVENT_DELAY, java.util.concurrent.TimeUnit.SECONDS)

    verify(config, atLeast(2)).indexProject(project)
  }

  @Test def corrupIndexExceptionWhenIndexing() {
    // When indexing a project fails with an CorruptIndexException we expect it to
    // try and index the project again later.
    val latch = new CountDownLatch(2)

    val config = mockIndexerConfigWithException(new CorruptIndexException(""))

    when(config.indexProject(project)).thenReturn(
        Failure(new CorruptIndexException("")))

    val job = ProjectIndexJob(config, project, INTERVAL)
    job.addJobChangeListener(new JobChangeAdapter {
      override def done(event: IJobChangeEvent): Unit = latch.countDown()
    })

    job.schedule()
    latch.await(EVENT_DELAY, java.util.concurrent.TimeUnit.SECONDS)

    verify(config, atLeast(2)).indexProject(project)
  }

}

object ProjectIndexJobTest
  extends TestProjectSetup("ProjectIndexJobTestProject", bundleName= "org.scala.tools.eclipse.search.tests")
     with TestUtil {

  val INTERVAL = 500

  class TestIndex extends Index {
    override val base: IPath = new Path(mkPath("target","project-index-job-test"))
  }


  def mockedSuccessfullIndexerConfig = {
    val index = mock(classOf[TestIndex])
    when(index.isIndexable(Matchers.argThat(mocks.args.anyInstance[IFile]))).thenReturn(true)
    when(index.removeOccurrencesFromFile(
        Matchers.argThat(mocks.args.anyInstance[IPath]),
        Matchers.argThat(mocks.args.anyInstance[IScalaProject]))).thenReturn(Success(()))

    val indexer = mock(classOf[SourceIndexer])
    when(indexer.index).thenReturn(index)
    when(indexer.indexProject(project)).thenReturn(Success(()))
    when(indexer.indexIFile(Matchers.argThat(mocks.args.anyInstance[IFile]))).thenReturn(Success(()))

    indexer
  }

  def mockedSuccessfullIndexerConfigReIndexing = {
    val indexer = mockedSuccessfullIndexerConfig
    when(indexer.index.indexExists(project.underlying)).thenReturn(false)
    indexer
  }

  def mockedSuccessfullIndexerConfigNoReindexing = {
    val indexer = mockedSuccessfullIndexerConfig
    when(indexer.index.indexExists(project.underlying)).thenReturn(true)
    indexer
  }

  def mockIndexerConfigWithException(ex: Exception) = {
    val index = mock(classOf[TestIndex])
    when(index.deleteIndex(project.underlying)).thenReturn(Success(true))
    when(index.indexExists(project.underlying)).thenReturn(false)

    val indexer = mock(classOf[SourceIndexer])
    when(indexer.index).thenReturn(index)
    when(indexer.indexProject(project)).thenReturn(Failure(ex))
    indexer
  }
}