package org.scala.tools.eclipse.search

import scala.tools.eclipse.javaelements.ScalaCompilationUnit

object SomeInterestingCode {
  
  def numberOfTypes(scalaCompilationUnit: ScalaCompilationUnit): Int = {
    scalaCompilationUnit.getCompilationUnit.getAllTypes.length
  }

}