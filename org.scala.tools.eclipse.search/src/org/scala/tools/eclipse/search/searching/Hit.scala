package org.scala.tools.eclipse.search.searching

import scala.tools.eclipse.InteractiveCompilationUnit

case class Hit(cu: InteractiveCompilationUnit, word: String, lineContent: String, offset: Int)