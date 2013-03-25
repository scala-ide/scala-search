package org.scala.tools.eclipse.search

import org.eclipse.core.resources.IResourceDelta
import org.eclipse.core.resources.IResourceDeltaVisitor
import org.eclipse.core.resources.IResourceDeltaVisitor

object EclipseImplicits {

  implicit def resourceDeltaVisitor(f: IResourceDelta => Boolean): IResourceDeltaVisitor = new IResourceDeltaVisitor {
    def visit(delta: IResourceDelta): Boolean = {
      f(delta)
    }
  }

}