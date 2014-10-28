package org.scala.tools.eclipse.search

import org.scalaide.core.testsetup.TestProjectSetup
import org.scalaide.core.testsetup.SDTTestUtils
import org.junit.{ Test, Before, After }
import org.junit.Assert._
import org.eclipse.core.runtime.NullProgressMonitor
import java.io.File
import org.scala.tools.eclipse.search.indexing.Index
import org.scala.tools.eclipse.search.indexing.SourceIndexer
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.runtime.Path
import java.util.concurrent.CountDownLatch
import org.eclipse.core.resources.IProject
import org.scalaide.core.IScalaPlugin
import org.junit.Ignore

class IndexJobManagerTest {

  import SDTTestUtils._
  import IndexJobManagerTest._

  @volatile var indexer: SourceIndexer = _
  @volatile var indexManager: IndexJobManager = _

  @Before
  def setup {

    // re-open the projec if needed
    if (!project.underlying.isOpen) {
      val latch = new CountDownLatch(1)

      val observer = ProjectChangeObserver(onOpen = (p: IProject) => {
        latch.countDown()
      })

      project.underlying.open(monitor)
      latch.await(EVENT_DELAY, java.util.concurrent.TimeUnit.SECONDS)

      assertTrue("unable to reopen the project before test", project.underlying.isOpen)
    }

    val index = new Index {
      override val base = new Path(mkPath("target", "index-job-manager-test"))
    }
    indexer = new SourceIndexer(index)
    indexManager = new IndexJobManager(indexer)
    indexManager.startup()
  }

  @After
  def teardown {
    indexer = null
    indexManager.shutdown()
    indexManager = null
  }

  @Test
  def canProgramaticallyStartAnIndexingJob() {
    // Precondition
    assertTrue(project.underlying.isOpen())

    // Event
    indexManager.startIndexing(project.underlying)

    // Result
    assertTrue(indexManager.isIndexing(project.underlying))
  }

  @Test
  def canProgramaticallyStopAnIndexingJob() {
    // Precondition
    assertTrue(project.underlying.isOpen())
    assertFalse(indexManager.isIndexing(project.underlying))
    indexManager.startIndexing(project.underlying)
    assertTrue(indexManager.isIndexing(project.underlying))

    // Event
    indexManager.stopIndexing(project.underlying)

    // Result
    assertFalse(indexManager.isIndexing(project.underlying))
  }

  @Test
  def startsIndexingJobWhenProjectIsOpened() {

    val latch = new CountDownLatch(1)

    val observer = ProjectChangeObserver(onOpen = (p: IProject) => {
      latch.countDown()
    })

    // preconditions
    assertTrue(project.underlying.isOpen())
    assertFalse(indexManager.isIndexing(project.underlying))

    // event
    project.underlying.close(monitor)
    project.underlying.open(monitor)
    latch.await(EVENT_DELAY, java.util.concurrent.TimeUnit.SECONDS)

    // reaction
    assertTrue(indexManager.isIndexing(project.underlying))
    observer.stop
  }

  @Test
  def startsIndexingJobWhenProjectIsCreated() {

    val name = "IndexJobManagerTest-ToBeCreated"
    val latch = new CountDownLatch(1)

    val observer = ProjectChangeObserver(onNewScalaProject = (p: IProject) => {
      if (p.getName == name)
        latch.countDown()
    })

    // event
    val p = createProjects(name).head
    latch.await(EVENT_DELAY, java.util.concurrent.TimeUnit.SECONDS)

    // reaction
    assertTrue(indexManager.isIndexing(p.underlying))

    observer.stop
  }

  @Test
  def stopIndexingJobWhenProjectIsClosed() {

    val latch = new CountDownLatch(1)

    val observer = ProjectChangeObserver(onClose = (p: IProject) => {
      latch.countDown()
    })

    // precondition
    assertTrue(project.underlying.isOpen())
    indexManager.startIndexing(project.underlying)
    assertTrue(indexManager.isIndexing(project.underlying))

    // event
    project.underlying.close(monitor)
    latch.await(EVENT_DELAY, java.util.concurrent.TimeUnit.SECONDS)

    // reaction
    assertFalse(project.underlying.isOpen())
    assertFalse(indexManager.isIndexing(project.underlying))
    observer.stop
  }

  @Test
  def stopIndexingJobWhenProjectIsDeleted() {

    val name = "IndexJobManagerTest-ToBeDeleted"
    val createdLatch = new CountDownLatch(1)
    val deletedLatch = new CountDownLatch(1)

    val observer = ProjectChangeObserver(
      onNewScalaProject = (p: IProject) => {
        if (p.getName == name)
          createdLatch.countDown()
      },
      onDelete = (p: IProject) => {
        if (p.getName == name)
          deletedLatch.countDown()
      })

    // set-up
    val p = createProjects(name).head
    createdLatch.await(EVENT_DELAY, java.util.concurrent.TimeUnit.SECONDS)

    // preconditions
    assertTrue(indexManager.isIndexing(p.underlying))

    // event
    p.underlying.delete(true, monitor)
    deletedLatch.await(EVENT_DELAY, java.util.concurrent.TimeUnit.SECONDS)

    // expected
    assertFalse(indexManager.isIndexing(p.underlying))
    observer.stop
  }

  @Test @Ignore("Flaky test, failing very often on Linux with OpenJDK")
  def deletesIndexWhenProjectIsDeleted() {
    val name = "IndexJobManagerTest-ToBeDeletedIndex"
    val fileName = "Test.scala"
    val createdLatch = new CountDownLatch(1)
    val deletedLatch = new CountDownLatch(1)
    val fileAddedLatch = new CountDownLatch(1)

    val observer = ProjectChangeObserver(
      onNewScalaProject = (p: IProject) => {
        if (p.getName == name)
          createdLatch.countDown()
      },
      onDelete = (p: IProject) => {
        if (p.getName == name)
          deletedLatch.countDown()
      })

    // set-up
    val p = createProjects(name).head
    createdLatch.await(EVENT_DELAY, java.util.concurrent.TimeUnit.SECONDS)

    val files = FileChangeObserver(p)(onAdded = file => {
      if (file.getName() == fileName)
        fileAddedLatch.countDown()
    })

    addSourceFile(p)(fileName, "class Test")

    // preconditions
    fileAddedLatch.await(EVENT_DELAY, java.util.concurrent.TimeUnit.SECONDS)

    // We can't use a latch here because we have to know when a specific file
    // has been indexed. Instead until the index has been created on disc, as
    // we know that will happen once it has indexed the file. We wait no longer
    // than 10 seconds.
    SDTTestUtils.waitUntil(10000)(indexer.index.location(p.underlying).toFile.exists)

    assertTrue(indexer.index.location(p.underlying).toFile.exists)

    // event
    p.underlying.delete(true, monitor)
    deletedLatch.await(EVENT_DELAY, java.util.concurrent.TimeUnit.SECONDS)

    // wait until the index has been removed
    SDTTestUtils.waitUntil(10000)(!indexer.index.location(p.underlying).toFile.exists)

    // expected
    assertFalse(indexer.index.location(p.underlying).toFile.exists)
    observer.stop
  }
}

object IndexJobManagerTest extends TestProjectSetup("IndexJobManagerTest", bundleName = "org.scala.tools.eclipse.search.tests")
  with TestUtil {

  val monitor = new NullProgressMonitor()

}
