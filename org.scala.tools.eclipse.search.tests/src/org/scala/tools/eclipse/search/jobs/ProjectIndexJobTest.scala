package org.scala.tools.eclipse.search.jobs

import java.io.IOException
import scala.tools.eclipse.testsetup.TestProjectSetup
import scala.util.Failure
import scala.util.Success
import org.apache.lucene.index.CorruptIndexException
import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.IProject
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
import scala.tools.eclipse.testsetup.SDTTestUtils

class ProjectIndexJobTest {

  import ProjectIndexJobTest._
  import SDTTestUtils._

  @Test def whenStartingItShouldTriggerIndexing() {
    // When the job is started it should start indexing the existing
    // files in the project.
    val latch = new CountDownLatch(1)

    val index = mock(classOf[Index])
    val indexer = mock(classOf[SourceIndexer])
    when(indexer.indexProject(project)).thenReturn(Success())

    val job = ProjectIndexJob(indexer, index, project, INTERVAL)
    job.addJobChangeListener(new JobChangeAdapter {
      override def done(event: IJobChangeEvent): Unit = latch.countDown()
    })

    job.schedule()
    latch.await(2, java.util.concurrent.TimeUnit.SECONDS)

    verify(indexer, atLeast(1)).indexProject(project)
  }

  @Test def changingFilesShouldTriggerIndexing() {
    // When a file is changed SourceIndexer.indexScalaFile should be invoke
    val latch = new CountDownLatch(2)
    val arg = mocks.args.fileNamed("Change.scala")

    val index = mock(classOf[Index])
    val indexer = mockedSuccessfullIndexer(index)
    when(indexer.indexIFile(Matchers.argThat(arg))).thenReturn(Success())

    val job = ProjectIndexJob(indexer, index, project, INTERVAL)
    job.addJobChangeListener(new JobChangeAdapter {
      override def done(event: IJobChangeEvent): Unit = latch.countDown()
    })

    addSourceFile(project.underlying)("Change.scala", "")
    addContent(project.underlying)("Change.scala", "class Foo")

    job.schedule()
    latch.await(2, java.util.concurrent.TimeUnit.SECONDS)

    verify(indexer, times(2)).indexIFile(Matchers.argThat(arg))
  }

  @Test def addingAFileShouldTriggerIndexing() {
    // When a file is added SourceIndexer.indexScalaFile should be invoke
    val latch = new CountDownLatch(1)
    val arg = mocks.args.fileNamed("Add.scala")

    val index = mock(classOf[Index])
    val indexer = mockedSuccessfullIndexer(index)

    val job = ProjectIndexJob(indexer, index, project, INTERVAL)
    job.addJobChangeListener(new JobChangeAdapter {
      override def done(event: IJobChangeEvent): Unit = latch.countDown()
    })

    addSourceFile(project.underlying)("Add.scala", "")

    job.schedule()
    latch.await(2, java.util.concurrent.TimeUnit.SECONDS)

    verify(indexer, times(1)).indexIFile(Matchers.argThat(arg))
  }

  @Test def deletingAFileShouldTriggerRemoval() {
    // When a file is deleted Index.removeOccurrencesFromFile should be invoked
    val latch = new CountDownLatch(1)
    val arg = mocks.args.pathEndingWith("Remove.scala")

    val index = mock(classOf[Index])
    val indexer = mockedSuccessfullIndexer(index)

    when(index.removeOccurrencesFromFile(
        Matchers.argThat(arg),
        Matchers.eq(project.underlying))).thenReturn(Success())

    val job = ProjectIndexJob(indexer, index, project, INTERVAL)
    job.addJobChangeListener(new JobChangeAdapter {
      override def done(event: IJobChangeEvent): Unit = latch.countDown()
    })

    addSourceFile(project.underlying)("Remove.scala", "")
    deleteSourceFile(project.underlying)("Remove.scala")

    job.schedule()
    latch.await(2, java.util.concurrent.TimeUnit.SECONDS)

    verify(index).removeOccurrencesFromFile(
        Matchers.argThat(arg),
        Matchers.eq(project.underlying))
  }


  @Test def invalidPCExceptionWhenIndexing() {
    // When indexing a project fails with an InvalidPresentationCompilerException we
    // don't want it to try and index the project again.
    val latch = new CountDownLatch(1)

    val index = mock(classOf[Index])
    val indexer = mock(classOf[SourceIndexer])
    when(indexer.indexProject(project)).thenReturn(
        Failure(new OccurrenceCollector.InvalidPresentationCompilerException("")))

    val job = ProjectIndexJob(indexer, index, project, INTERVAL)
    job.addJobChangeListener(new JobChangeAdapter {
      override def done(event: IJobChangeEvent): Unit = latch.countDown()
    })

    job.schedule()
    latch.await(2, java.util.concurrent.TimeUnit.SECONDS)

    verify(indexer, times(1)).indexProject(project)
  }

  @Test def ioExceptionWhenIndexing() {
    // When indexing a project fails with an IOException we expect it to
    // try and index the project again later.
    val latch = new CountDownLatch(2)

    val index = mock(classOf[Index])
    val indexer = mock(classOf[SourceIndexer])

    when(indexer.indexProject(project)).thenReturn(
        Failure(new IOException))

    val job = ProjectIndexJob(indexer, index, project, INTERVAL)
    job.addJobChangeListener(new JobChangeAdapter {
      override def done(event: IJobChangeEvent): Unit = latch.countDown()
    })

    job.schedule()
    latch.await(2, java.util.concurrent.TimeUnit.SECONDS)

    verify(indexer, atLeast(2)).indexProject(project)
  }

  @Test def corrupIndexExceptionWhenIndexing() {
    // When indexing a project fails with an CorruptIndexException we expect it to
    // try and index the project again later.
    val latch = new CountDownLatch(2)

    val index = mock(classOf[Index])
    val indexer = mock(classOf[SourceIndexer])

    when(indexer.indexProject(project)).thenReturn(
        Failure(new CorruptIndexException("")))

    val job = ProjectIndexJob(indexer, index, project, INTERVAL)
    job.addJobChangeListener(new JobChangeAdapter {
      override def done(event: IJobChangeEvent): Unit = latch.countDown()
    })

    job.schedule()
    latch.await(2, java.util.concurrent.TimeUnit.SECONDS)

    verify(indexer, atLeast(2)).indexProject(project)
  }

}

object ProjectIndexJobTest
  extends TestProjectSetup("ProjectIndexJobTestProject", bundleName= "org.scala.tools.eclipse.search.tests")
     with TestUtil {

  val INTERVAL = 500
  val MAX_WAIT = 2000

  def mockedSuccessfullIndexer(index: Index) = {
    val indexer = mock(classOf[SourceIndexer])
    when(indexer.indexProject(project)).thenReturn(Success(()))
    when(indexer.indexIFile(Matchers.argThat(mocks.args.anyInstance[IFile]))).thenReturn(Success(()))
    when(index.removeOccurrencesFromFile(
        Matchers.argThat(mocks.args.anyInstance[IPath]),
        Matchers.argThat(mocks.args.anyInstance[IProject]))).thenReturn(Success(()))
    indexer
  }

  class JobChangeAdapter extends IJobChangeListener {
    override def aboutToRun(event: IJobChangeEvent): Unit = {}
    override def awake(event: IJobChangeEvent): Unit = {}
    override def done(event: IJobChangeEvent): Unit = {}
    override def running(event: IJobChangeEvent): Unit = {}
    override def scheduled(event: IJobChangeEvent): Unit = {}
    override def sleeping(event: IJobChangeEvent): Unit = {}
  }
}