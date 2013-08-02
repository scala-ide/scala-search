package org.scala.tools.eclipse.search.searching

import org.junit.Assert._
import org.junit.Test
import org.scala.tools.eclipse.search.TestUtil
import org.eclipse.core.resources.ResourcesPlugin
import scala.tools.eclipse.testsetup.TestProjectSetup
import org.junit.Before
import org.eclipse.core.runtime.NullProgressMonitor
import scala.tools.eclipse.testsetup.SDTTestUtils

class ProjectFinderTest extends ProjectFinder {

  import ProjectFinderTest._

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
   * (B)-->(C)
   *        |
   *        |
   *       (D)
   *
   */

  @Before
  def setup {

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

    SDTTestUtils.waitUntil(10000)(setupIsReady)

    // Making sure the set-up is as we expect it to be.

    assertTrue(projectA.isOpen)
    assertTrue(projectB.isOpen)
    assertTrue(projectC.isOpen)
    assertTrue(projectD.isOpen)
    assertTrue(projectE.isOpen)

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

}

object ProjectFinderTest extends TestUtil {

  val names = List("A","B","C","D")

  val projectA = SDTTestUtils.setupProject("A", "org.scala.tools.eclipse.search.tests").underlying
  val projectB = SDTTestUtils.setupProject("B", "org.scala.tools.eclipse.search.tests").underlying
  val projectC = SDTTestUtils.setupProject("C", "org.scala.tools.eclipse.search.tests").underlying
  val projectD = SDTTestUtils.setupProject("D", "org.scala.tools.eclipse.search.tests").underlying
  val projectE = SDTTestUtils.setupProject("E", "org.scala.tools.eclipse.search.tests").underlying

}