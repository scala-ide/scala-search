package org.scala.tools.eclipse.search

import java.util.concurrent.CountDownLatch
import scala.tools.eclipse.ScalaProject
import scala.tools.eclipse.javaelements.ScalaSourceFile
import org.eclipse.core.resources.IProject
import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.core.runtime.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.scala.tools.eclipse.search.indexing.Index
import org.scala.tools.eclipse.search.indexing.SourceIndexer
import org.scala.tools.eclipse.search.searching.SourceCreator
import IndexJobManagerTest.EVENT_DELAY
import IndexJobManagerTest.Project
import IndexJobManagerTest.mkPath
import scala.util.Try
import org.eclipse.core.resources.IFile
import scala.tools.eclipse.testsetup.SDTTestUtils
import scala.tools.eclipse.logging.HasLogger

class IndexJobManagerTest extends HasLogger {

  import IndexJobManagerTest._

  @Test
  def canProgramaticallyStartTrackingProject() {
    // Precondition
    val project = Project("CanProgramaticallyStartTrackingProject")
    val manager = anonymousManager(project)
    // Event
    manager.startTrackingChanges(project.scalaProject.underlying)
    // Result
    assertTrue("Expected that the manger was tracking changes, it isn't", manager.isTrackingChanges(project.scalaProject.underlying))
    manager.shutdown
  }

  @Test
  def canProgramaticallyStopTracking() {
    // Precondition
    val project = Project("CanProgramaticallyStopTracking")
    val manager = anonymousManager(project)
    assertTrue(project.scalaProject.underlying.isOpen())
    assertFalse(manager.isTrackingChanges(project.scalaProject.underlying))
    manager.startTrackingChanges(project.scalaProject.underlying)
    assertTrue(manager.isTrackingChanges(project.scalaProject.underlying))
    // Event
    manager.stopTrackingChanges(project.scalaProject.underlying)
    // Result
    assertFalse("Expected the the manager stopped tracking changes. It didn't.", manager.isTrackingChanges(project.scalaProject.underlying))
    manager.shutdown
  }

  @Test
  def startsIndexingJobWhenProjectIsOpened() {
    val name = "StartsIndexingJobWhenProjectIsOpened"

    val latch = new CountDownLatch(1)
    val observer = ProjectChangeObserver(onOpen = (p: IProject) => {
      latch.countDown()
    })

    testStandardSetup(name) { (manager, project) =>
      // preconditions
      assertTrue("Expected the project to be open", project.scalaProject.underlying.isOpen())
      // event
      project.scalaProject.underlying.close(monitor)
      project.scalaProject.underlying.open(monitor)
      latch.await(EVENT_DELAY, java.util.concurrent.TimeUnit.SECONDS)
      // reaction
      assertTrue("Expected that the manger was tracking changes, it isn't", manager.isTrackingChanges(project.scalaProject.underlying))
    }

    observer.stop
  }

  @Test
  def startsIndexingJobWhenProjectIsCreated() {

    val name = "StartsIndexingJobWhenProjectIsCreated"
    val latch = new CountDownLatch(1)

    val observer = ProjectChangeObserver(onNewScalaProject = (p: IProject) => {
      if (p.getName == name)
        latch.countDown()
    })

    testStandardSetup(name) { (manager, project) =>
      latch.await(EVENT_DELAY, java.util.concurrent.TimeUnit.SECONDS)
      assertTrue(manager.isTrackingChanges(project.scalaProject.underlying))
    }

    observer.stop
  }

  @Test
  def stopIndexingJobWhenProjectIsClosed() {
    val name = "StopIndexingJobWhenProjectIsClosed"

    val latch = new CountDownLatch(1)

    val observer = ProjectChangeObserver(onClose = (p: IProject) => {
      latch.countDown()
    })

    testStandardSetup(name) { (manager, project) => 
      // precondition
      assertTrue("Expected the project to be open", project.scalaProject.underlying.isOpen())
      assertTrue("Expected the manger to be tracking changes", manager.isTrackingChanges(project.scalaProject.underlying))
      // event
      project.scalaProject.underlying.close(monitor)
      latch.await(EVENT_DELAY, java.util.concurrent.TimeUnit.SECONDS)
      // reaction
      assertFalse("The project should be closed, but it's open", project.scalaProject.underlying.isOpen())
      assertFalse("The manager shouldn't be tracking changes, but it is", manager.isTrackingChanges(project.scalaProject.underlying))
    }

    observer.stop
  }

  @Test
  def stopIndexingJobWhenProjectIsDeleted() {

    val name = "StopIndexingJobWhenProjectIsDeleted"
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

    testStandardSetup(name) { (manager, project) =>
      // preconditions
      createdLatch.await(EVENT_DELAY, java.util.concurrent.TimeUnit.SECONDS)
      assertTrue(manager.isTrackingChanges(project.scalaProject.underlying))
      // event
      project.scalaProject.underlying.delete(true, monitor)
      deletedLatch.await(EVENT_DELAY, java.util.concurrent.TimeUnit.SECONDS)
      // expected
      assertFalse("Expected the manger to have stopped tracking changes", manager.isTrackingChanges(project.scalaProject.underlying))
    }

    observer.stop
  }

  @Test
  def deletesIndexWhenProjectIsDeleted() {
    val name = "DeletesIndexWhenProjectIsDeleted"

    @volatile var invoked = false
    val invokedLatch = new CountDownLatch(1)

    val index = new Index {
      override val base = new Path(mkPath("target",name))
      override def deleteIndex(project: IProject): Try[Boolean] = {
        invoked = true
        invokedLatch.countDown()
        super.deleteIndex(project)
      }
    }

    testWithCustomIndex(name, index) { (_,project) =>
      project.scalaProject.underlying.delete(true, monitor)
      invokedLatch.await(EVENT_DELAY, java.util.concurrent.TimeUnit.SECONDS)
      assertTrue("Should've invoked deleteIndex but didn't", invoked)
    }
  }

  @Test
  def theManagerMakesSureNewlyAddedFilesAreIndexed {
    val name = "TheManagerMakesSureNewlyAddedFilesAreIndexed"
    val latch = new CountDownLatch(1)
    val filename = s"$name.scala"

    @volatile var didInvoke = false

    val indexer = new SourceIndexer(indexNamed(name)){
      override def indexIFile(file: IFile) = {
        latch.countDown
        didInvoke = true
        super.indexIFile(file)
      }
    }

    testWithCustomIndexer(name, indexer) { (_,project) =>
      val file = project.create(filename)("class ItSchedulesNewlyAddedFilesForIndexing")
      latch.await(EVENT_DELAY, java.util.concurrent.TimeUnit.SECONDS)
      assertTrue("Expected it to have invoke indexIFile, it didn't", didInvoke)
    }
  }

  @Test
  def theManagerMakesSureChangedFilesAreIndexed = {
    val name = "TheManagerMakesSureChangedFilesAreIndexed"
    val latch = new CountDownLatch(2)
    val filename = s"$name.scala"

    @volatile var invocations = 0

    val indexer = new SourceIndexer(indexNamed(name)){
      override def indexIFile(file: IFile) = {
        latch.countDown
        invocations = invocations + 1
        super.indexIFile(file)
      }
    }

    testWithCustomIndexer(name, indexer) { (_,project) =>
      val file = project.create(filename)("")
      file.addContent(s"class $name")
      latch.await(EVENT_DELAY, java.util.concurrent.TimeUnit.SECONDS)
      logger.debug("NOW WE CHECK THE ASSERTION")
      assertEquals("Expected it to have invoke indexIFile, it didn't", 2, invocations)
    }
  }

  @Test
  def theManagerMakesSureDeletedFilesAreRemoved = {
    val name = "TheManagerMakesSureDeletedFilesAreRemoved"
    val filename = s"$name.scala"
    val project = Project(name)

    val latch = new CountDownLatch(1)
    @volatile var invocations = 0

    val index = new Index {
      override val base = new Path(mkPath("target",name))
      override def removeOccurrencesFromFile(path: IPath, project: ScalaProject): Try[Unit] = {
        if (path.lastSegment() == filename) {
          invocations = invocations + 1
          latch.countDown
        }
        super.removeOccurrencesFromFile(path, project)
      }
    }
    val indexer = new SourceIndexer(index)
    val manager = new IndexJobManager(indexer)

    val addedLatch = new CountDownLatch(1)
    FileChangeObserver(project.scalaProject)(onAdded = f => { addedLatch.countDown() })

    val file = project.create(filename)("")
    addedLatch.await(EVENT_DELAY, java.util.concurrent.TimeUnit.SECONDS)
    manager.startup
    manager.startTrackingChanges(project.scalaProject.underlying)
    SDTTestUtils.waitUntil(10000)(false) // Waiting for JDT to finish with the file.
    file.delete
    latch.await(EVENT_DELAY, java.util.concurrent.TimeUnit.SECONDS)
    assertEquals("Expected it to have invoked removeOccurrencesFromFile, it didn't", 1, invocations)
  }

}

object IndexJobManagerTest extends TestUtil with SourceCreator {

  val monitor = new NullProgressMonitor()

  def indexNamed(name: String) = new Index {
    override val base = new Path(mkPath("target",name))
  }

  def anonymousManager(indexName: String): IndexJobManager = {
    val index = indexNamed(indexName)
    val indexer = new SourceIndexer(index)
    val indexManager = new IndexJobManager(indexer)
    indexManager
  }

  def anonymousManager(p: Project): IndexJobManager = {
    val manager = anonymousManager(p.name)
    manager.startup()
    manager
  }

  def testStandardSetup(name: String)(f: (IndexJobManager, Project) => Unit) = {
    val manager = anonymousManager(name)
    testWithCustomManager(name, manager)(f)
  }

  def testWithCustomIndex(name: String, index: Index)(f: (IndexJobManager, Project) => Unit): Unit = {
    val indexer = new SourceIndexer(index)
    testWithCustomIndexer(name, indexer)(f)
  }

  def testWithCustomIndexer(name: String, indexer: SourceIndexer)(f: (IndexJobManager, Project) => Unit): Unit = {
    val manager = new IndexJobManager(indexer)
    testWithCustomManager(name, manager)(f)
  }

  def testWithCustomManager(name: String, manager: IndexJobManager)(f: (IndexJobManager, Project) => Unit): Unit = {
    manager.startup()
    val project = Project(name)
    f(manager, project)
    manager.shutdown
  }

}