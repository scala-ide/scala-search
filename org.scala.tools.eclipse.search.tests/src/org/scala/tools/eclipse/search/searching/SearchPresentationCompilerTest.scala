package org.scala.tools.eclipse.search.searching

import scala.tools.eclipse.testsetup.TestProjectSetup
import org.junit.Test
import org.scala.tools.eclipse.search.TestUtil
import org.junit.Assert._

class SearchPresentationCompilerTest {

  import SearchPresentationCompilerTest._
  import SearchPresentationCompiler._

  @Test def canGetSymbolAtLocation {
    val unit = scalaCompilationUnit(mkPath("org", "example", "ExampleWithSymbol.scala"))
    unit.withSourceFile { (cu, pc) =>
      val symbol = pc.symbolAt(Location(unit, 34), cu).get
      assertEquals("x", pc.askOption( () => symbol.nameString ).get)
    }(fail("withSourceFile call failed unexpectedly"))
  }

  @Test def noneIfAskedForWrongLocation {
    val unit = scalaCompilationUnit(mkPath("org", "example", "ExampleWithSymbol.scala"))
    unit.withSourceFile { (cu, pc) =>
      val symbol = pc.symbolAt(Location(unit, 108), cu) // 108 doesn't exist.
      assertEquals(true, symbol.isEmpty)
    }(fail("withSourceFile call failed unexpectedly"))
  }

}

object SearchPresentationCompilerTest
  extends TestProjectSetup("SearchPresentationCompilerTest", bundleName= "org.scala.tools.eclipse.search.tests") 
     with TestUtil