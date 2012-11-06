package org.scala.tools.eclipse.search

import org.junit.Test
import org.junit.Assert._
import scala.tools.eclipse.testsetup.TestProjectSetup

class SomeInterestingCodeTest extends TestProjectSetup("aProject", bundleName= "org.scala.tools.eclipse.search.tests") {
  
  @Test
  def numberOfTypes() {
    val compilationUnit= scalaCompilationUnit("org/example/ScalaClass.scala")
    
    assertEquals("Wrong number of types", 2, SomeInterestingCode.numberOfTypes(compilationUnit))
    
  }

}
