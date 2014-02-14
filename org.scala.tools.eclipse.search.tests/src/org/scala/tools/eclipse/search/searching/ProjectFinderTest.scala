package org.scala.tools.eclipse.search.searching

import org.junit.Assert._
import org.junit.Test
import org.scala.tools.eclipse.search.TestUtil
import org.eclipse.core.resources.ResourcesPlugin
import org.scalaide.core.testsetup.TestProjectSetup
import org.scalaide.core.testsetup.SDTTestUtils
import org.junit.Before
import org.eclipse.core.runtime.NullProgressMonitor
import org.scala.tools.eclipse.search.ProjectChangeObserver
import java.util.concurrent.CountDownLatch
import org.eclipse.core.resources.IProject

class ProjectFinderTest {

  import ProjectFinderTest._
  import ProjectFinder._

  /* The set-up looks like this:
   *
   * A has no dependencies
   * B depends on A and C
   * C depends on A
   * D depends on C
   * E has no dependencies
   *
   *    (A)        (E)
   *    / \
   *   /   \
   * (B)––>(C)
   *        |
   *        |
   *       (D)
   *
   */

  @Before
  def setup {

    Set(projectA, projectB, projectC, projectD, projectE) foreach ensureIsOpen

    def setupIsReady = {
       0 == projectA.getReferencedProjects().size   &&
      (2 == projectA.getReferencingProjects().size) && // A and C
      (2 == projectB.getReferencedProjects().size)  && // A and C
      (0 == projectB.getReferencingProjects().size) &&
      (1 == projectC.getReferencedProjects().size)  && // A
      (2 == projectC.getReferencingProjects().size) && // B and D
      (1 == projectD.getReferencedProjects().size)  && // C
      (0 == projectD.getReferencingProjects().size) &&
      (0 == projectE.getReferencedProjects().size)  &&
      (0 == projectE.getReferencingProjects().size)
    }

    // Can't get rid of this. Just because the projects are open doesn't
    // mean that the getReferencedProjects and getReferencingProjects are
    // initialized properly yet. Aparrently. If we remove this block
    // the conditions below will fail.
    SDTTestUtils.waitUntil(10000)(setupIsReady)

    assertEquals(0, projectA.getReferencedProjects().size)
    assertEquals(2, projectA.getReferencingProjects().size) // A and C
    assertEquals(2, projectB.getReferencedProjects().size)  // A and C
    assertEquals(0, projectB.getReferencingProjects().size)
    assertEquals(1, projectC.getReferencedProjects().size)  // A
    assertEquals(2, projectC.getReferencingProjects().size) // B and D
    assertEquals(1, projectD.getReferencedProjects().size)  // C
    assertEquals(0, projectD.getReferencingProjects().size)
    assertEquals(0, projectE.getReferencedProjects().size)
    assertEquals(0, projectE.getReferencingProjects().size)
  }

  @Test def closureOfA {
    // Tests that we find projects that depend on this project, transitively (I.e. B,C and D)
    val closure = projectClosure(projectA)
    val projects = closure.toSeq.map(_.getName)
    assertTrue(names.forall(projects.contains(_)))
  }

  @Test def closureOfB {
    // Tests that we find all project dependencies (I.e. both A and C)
    val closure = projectClosure(projectB)
    val projects = closure.toSeq.map(_.getName)
    assertTrue(names.forall(projects.contains(_)))
  }

  @Test def closureOfC {
    // Tests that we get projects that are referencing projects we depend on.
    val closure = projectClosure(projectC)
    val projects = closure.toSeq.map(_.getName)
    assertTrue(names.forall(projects.contains(_)))
  }

  @Test def closureOfD {
    // Tests that we get dependencies of dependencies (i.e. A is reachable from D)
    val closure = projectClosure(projectD)
    val projects = closure.toSeq.map(_.getName)
    assertTrue(names.forall(projects.contains(_)))
  }

  @Test def closureOfE {
    // Tests that that project E doesn't have any dependencies. This is to make sure it doesn't return
    // dependencies for projects that don't have any.
    val closure = projectClosure(projectE)
    assertEquals(Set(projectE), closure)
  }

  @Test def dontListProjectsThatAreClosed {
    // Projects that are closed should not be included in the
    // closure.

    val latch = new CountDownLatch(1)

    ProjectChangeObserver( onClose = p => {
      if (p.getName == projectA.getName) {
        latch.countDown
      }
    })

    projectA.close(new NullProgressMonitor)
    latch.await(EVENT_DELAY, java.util.concurrent.TimeUnit.SECONDS)
    val closure = projectClosure(projectD)

    assertEquals(Set(projectB, projectC, projectD), closure)
  }

}

object ProjectFinderTest extends TestUtil {

  val names = List("A","B","C","D")

  val projectA = SDTTestUtils.setupProject("A", "org.scala.tools.eclipse.search.tests").underlying
  val projectB = SDTTestUtils.setupProject("B", "org.scala.tools.eclipse.search.tests").underlying
  val projectC = SDTTestUtils.setupProject("C", "org.scala.tools.eclipse.search.tests").underlying
  val projectD = SDTTestUtils.setupProject("D", "org.scala.tools.eclipse.search.tests").underlying
  val projectE = SDTTestUtils.setupProject("E", "org.scala.tools.eclipse.search.tests").underlying

  def ensureIsOpen(p: IProject): Unit = {
    if (!p.isOpen) {
      val latch = new CountDownLatch(1)
      ProjectChangeObserver( onOpen = proj => {
        if (proj.getName == p.getName) {
          latch.countDown
        }
      })
      p.open(new NullProgressMonitor)
      latch.await(EVENT_DELAY, java.util.concurrent.TimeUnit.SECONDS)
      if (!p.isOpen) {
        fail("Wasn't able to open project " + p.getName)
      }
    }
  }

}