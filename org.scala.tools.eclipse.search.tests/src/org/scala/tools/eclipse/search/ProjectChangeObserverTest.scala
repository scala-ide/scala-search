
package org.scala.tools.eclipse.search

import scala.tools.eclipse.testsetup.TestProjectSetup
import org.junit.Test
import org.junit.Assert._
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.core.resources.ResourcesPlugin
import scala.tools.eclipse.testsetup.SDTTestUtils
import scala.tools.eclipse.testsetup.SDTTestUtils.waitUntil
import java.util.concurrent.CountDownLatch
import scala.tools.eclipse.ScalaProject
import org.eclipse.core.resources.IProject

class ProjectChangeObserverTest {

  import SDTTestUtils._
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