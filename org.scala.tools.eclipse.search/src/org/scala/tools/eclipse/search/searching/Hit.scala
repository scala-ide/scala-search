package org.scala.tools.eclipse.search.searching

import org.scalaide.core.compiler.InteractiveCompilationUnit

case class Hit(cu: InteractiveCompilationUnit, word: String, lineContent: String, offset: Int, callerOffset:Option[Int])