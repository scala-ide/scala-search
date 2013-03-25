package org.scala.tools.eclipse.search

import org.eclipse.core.resources.IResourceChangeListener
import org.eclipse.core.resources.ResourcesPlugin

class Observing(listener: IResourceChangeListener) {

  def stop: Unit = {
    ResourcesPlugin.getWorkspace().removeResourceChangeListener(listener)
  }

}