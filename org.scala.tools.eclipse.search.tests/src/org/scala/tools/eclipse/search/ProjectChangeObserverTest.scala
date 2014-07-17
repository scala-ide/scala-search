
package org.scala.tools.eclipse.search

import java.util.concurrent.CountDownLatch

import org.eclipse.core.resources.IProject
import org.eclipse.core.runtime.NullProgressMonitor
import org.junit.Assert.assertEquals
import org.junit.Test
import org.scalaide.core.testsetup.SDTTestUtils

class ProjectChangeObserverTest {
  import ProjectChangeObserverTest._

  @Test def reactsToOpenEvents() {
    // set-up
    val latch = new CountDownLatch(1)
    @volatile var fired = false

    ProjectChangeObserver(onOpen = (project: IProject) => {
      latch.countDown()
      fired = true
    })

    //event
    val project = SDTTestUtils.createProjects("ProjectChangeObserverTest-ToBeCreated").head
    project.underlying.open(monitor)

    // expected
    latch.await(EVENT_DELAY, java.util.concurrent.TimeUnit.SECONDS)
    assertEquals("It should've reacted to the open event", true, fired)
  }

  @Test def reactsToCloseEvents() {
    // set-up
    val latch = new CountDownLatch(1)
    @volatile var fired = false

    ProjectChangeObserver(onClose = (project: IProject) => {
      latch.countDown()
      fired = true
    })

    //event
    val project = SDTTestUtils.createProjects("ProjectChangeObserverTest-ToBeClosed").head
    project.underlying.open(monitor)
    project.underlying.close(monitor)

    // expected
    latch.await(EVENT_DELAY, java.util.concurrent.TimeUnit.SECONDS)
    assertEquals("It should've reacted to the close event", true, fired)
  }

  @Test def reactsToDeleteEvents() {

    // set-up
    val latch = new CountDownLatch(1)
    @volatile var fired = false

    ProjectChangeObserver(onDelete = (project: IProject) => {
      latch.countDown()
      fired = true
    })

    //event
    val project = SDTTestUtils.createProjects("ProjectChangeObserverTest-ToBeDeleted").head
    project.underlying.open(monitor)
    project.underlying.delete(true, monitor)

    // expected
    latch.await(EVENT_DELAY, java.util.concurrent.TimeUnit.SECONDS)
    assertEquals("It should've reacted to the delete event", true, fired)
  }

}

object ProjectChangeObserverTest
  extends TestUtil {

  val monitor = new NullProgressMonitor()

}