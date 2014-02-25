package org.scala.tools.eclipse.search.searching

import org.scalaide.core.internal.jdt.model.ScalaSourceFile
import org.scalaide.core.compiler.InteractiveCompilationUnit

case class Location(cu: InteractiveCompilationUnit, offset: Int)