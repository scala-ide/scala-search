package org.scala.tools.eclipse.search

import scala.tools.eclipse.testsetup.TestProjectSetup
import org.junit.{ Test, Before, After }
import org.junit.Assert._
import org.eclipse.core.runtime.NullProgressMonitor
import java.io.File
import org.scala.tools.eclipse.search.indexing.Index
import org.scala.tools.eclipse.search.indexing.SourceIndexer
import scala.tools.eclipse.testsetup.SDTTestUtils
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.runtime.Path
import java.util.concurrent.CountDownLatch
import org.eclipse.core.resources.IProject
import scala.tools.eclipse.ScalaPlugin

class IndexJobManagerTest {

  import SDTTestUtils._
  import IndexJobManagerTest._

  var config: Index with SourceIndexer with IndexJobManager = _

  @Before
  def setup {
    config = new Index with SourceIndexer with IndexJobManager with Lifecycle {
      override val base = new Path(mkPath("target","index-job-manager-test"))
    }
    config.startup()
  }

  @After
  def teardown {
    config.shutdown()
    config = null
  }

  @Test
  def canProgramaticallyStartAnIndexingJob() {
    // Precondition
    assertTrue(project.underlying.isOpen())

    // Event
    config.startIndexing(project.underlying)

    // Result
    assertTrue(config.isIndexing(project.underlying))
  }

  @Test
  def canProgramaticallyStopAnIndexingJob() {
    // Precondition
    assertTrue(project.underlying.isOpen())
    assertFalse(config.isIndexing(project.underlying))
    config.startIndexing(project.underlying)
    assertTrue(config.isIndexing(project.underlying))

    // Event
    config.stopIndexing(project.underlying)

    // Result
    assertFalse(config.isIndexing(project.underlying))
  }

  @Test
  def startsIndexingJobWhenProjectIsOpened() {

    val latch = new CountDownLatch(1)

    val observer = ProjectChangeObserver(onOpen = (p: IProject) => {
      latch.countDown()
    })

    // preconditions
    assertTrue(project.underlying.isOpen())
    assertFalse(config.isIndexing(project.underlying))

    // event
    project.underlying.close(monitor)
    project.underlying.open(monitor)
    latch.await(EVENT_DELAY, java.util.concurrent.TimeUnit.SECONDS)

    // reaction
    assertTrue(config.isIndexing(project.underlying))
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
    assertTrue(config.isIndexing(p.underlying))

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
    config.startIndexing(project.underlying)
    assertTrue(config.isIndexing(project.underlying))

    // event
    project.underlying.close(monitor)
    latch.await(EVENT_DELAY, java.util.concurrent.TimeUnit.SECONDS)

    // reaction
    assertFalse(project.underlying.isOpen())
    assertFalse(config.isIndexing(project.underlying))
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
    assertTrue(config.isIndexing(p.underlying))

    // event
    p.underlying.delete(true, monitor)
    deletedLatch.await(EVENT_DELAY, java.util.concurrent.TimeUnit.SECONDS)

    // expected
    assertFalse(config.isIndexing(p.underlying))
    observer.stop
  }

  @Test
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
    SDTTestUtils.waitUntil(10000)(config.location(p.underlying).toFile.exists)

    assertTrue(config.location(p.underlying).toFile.exists)

    // event
    p.underlying.delete(true, monitor)
    deletedLatch.await(EVENT_DELAY, java.util.concurrent.TimeUnit.SECONDS)

    // expected
    assertFalse(config.location(p.underlying).toFile.exists)
    observer.stop
  }
}

object IndexJobManagerTest extends TestProjectSetup("IndexJobManagerTest", bundleName= "org.scala.tools.eclipse.search.tests")
                             with TestUtil {

  val monitor = new NullProgressMonitor()

}