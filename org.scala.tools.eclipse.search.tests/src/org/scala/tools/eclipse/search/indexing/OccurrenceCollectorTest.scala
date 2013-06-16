package org.scala.tools.eclipse.search.indexing

import org.junit.Test
import org.junit.Assert._
import scala.tools.eclipse.testsetup.TestProjectSetup
import scala.util.Failure
import scala.util.Success
import org.scala.tools.eclipse.search.TestUtil

object OccurrenceCollectorTest extends TestProjectSetup("aProject", bundleName= "org.scala.tools.eclipse.search.tests")
                                  with TestUtil {

  def occurrenceFor(word: String, occurrences: Seq[Occurrence]) = {
    occurrences.filter( _.word == word )
  }

  def doWithOccurrencesInUnit(path: String*)(f: Seq[Occurrence] => Unit): Unit = {
    val unit = scalaCompilationUnit(mkPath(path:_*))
    val occurrences = OccurrenceCollector.findOccurrences(unit)
    occurrences match {
      case Failure(f) => fail(s"Got an unexpected failure when finding occurrences ${f.getMessage()}")
      case Success(occs) => f(occs)
    }
  }

}

/**
 * This tests the occurrence collector exclusively, this doesn't depend on any for of index.
 */
class OccurrenceCollectorTest {

  import OccurrenceCollectorTest._

  @Test
  def numberOfMethods() {
    doWithOccurrencesInUnit("org","example","ScalaClass.scala") { occurrences =>
      val methodOne = occurrenceFor("methodOne", occurrences)
      val methodTwo = occurrenceFor("methodTwo", occurrences)
      val methodThree = occurrenceFor("methodThree", occurrences)
      assertEquals("Should be two occurrences of methodOne %s".format(methodOne), 2, methodOne.size)
      assertEquals("Should be two occurrences of methodTwo %s".format(methodTwo), 2, methodTwo.size)
      assertEquals("Should be two occurrences of methodThree %s".format(methodThree), 2, methodThree.size)
    }
  }

  @Test
  def methodChaining() {
    doWithOccurrencesInUnit("org","example","MethodChaining.scala") { occurrences => 
      val foo = occurrenceFor("foo", occurrences)
      val bar = occurrenceFor("bar", occurrences)
      assertEquals("Should be two occurrences of foo %s".format(foo), 2, foo.size)
      assertEquals("Should be two occurrences of bar %s".format(bar), 2, bar.size)
    }
  }

  @Test def invocationAsArgument() {
    doWithOccurrencesInUnit("org","example","InvocationAsArgument.scala") { occurrences => 
      val m = occurrenceFor("methodTwo", occurrences)
      assertEquals("Should be 3 occurrences of methodTwo %s".format(m), 3, m.size)
    }
  }

  @Test def selectInApply() {
    doWithOccurrencesInUnit("org","example","SelectInApply.scala") { occurrences =>
      val x = occurrenceFor("x", occurrences)
      assertEquals("Should be 2 occurrences of x %s".format(x), 2, x.size)
    }
  }

  @Test def stringInterpolation() {
    doWithOccurrencesInUnit("org","example","StringInterpolation.scala") { occurrences =>
      val x = occurrenceFor("x", occurrences)
      assertEquals("Should be 2 occurrences of x %s".format(x), 2, x.size)
    }
  }

  @Test def annotationsOnMethods() {
    doWithOccurrencesInUnit("org","example", "Annotations.scala") { occurrences =>
      val x = occurrenceFor("IOException", occurrences)
      assertEquals("Should be 1 occurrences of IOException %s".format(x), 1, x.size)
    }
  }

  @Test def recordsOccurrencesOfSyntheticEmptyConstructor() {
    doWithOccurrencesInUnit("org", "example", "SyntheticEmptyConstructor.scala") { occurrences =>
      val x = occurrenceFor("<init>", occurrences).filter(_.occurrenceKind == Declaration)
      assertEquals("Should find 1 synthetic empty constructor", 1, x.size)
    }
  }

  @Test def recordsOccurrencesOfSyntheticConstructor() {
    doWithOccurrencesInUnit("org", "example", "SyntheticConstructor.scala") { occurrences =>
      val x = occurrenceFor("<init>", occurrences).filter(_.occurrenceKind == Declaration)
      assertEquals("Should find 1 synthetic constructor", 1, x.size)
    }
  }

  @Test def recordsOccurrencesOfExplicitConstructor() {
    doWithOccurrencesInUnit("org", "example", "ExplicitConstructor.scala") { occurrences =>
      val x = occurrenceFor("<init>", occurrences).filter(_.occurrenceKind == Declaration)
      assertEquals("Should find 2 constructors", 2, x.size)
    }
  }

  @Test def findInvocationsOfConstructors() {
    doWithOccurrencesInUnit("org", "example", "ConstructorInvocations.scala") { occurrences =>
      // we expect 3 here because the constructors created by the compiler calls super.<init>.
      // The compiler generates a constructor for the class and the companion object.
      val r =  occurrenceFor("<init>", occurrences)
      val x = r.filter(o => o.occurrenceKind == Reference && o.offset == 84 )
      assertEquals("Should find 1 constructors invocation", 1, x.size)
    }
  }

}
