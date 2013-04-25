package org.scala.tools.eclipse.search.searching

import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.InteractiveCompilationUnit

case class Location(cu: InteractiveCompilationUnit, offset: Int)