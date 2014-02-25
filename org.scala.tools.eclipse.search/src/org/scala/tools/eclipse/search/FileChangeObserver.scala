package org.scala.tools.eclipse.search

import org.scalaide.logging.HasLogger
import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.IResource
import org.eclipse.core.resources.IResourceChangeEvent
import org.eclipse.core.resources.IResourceChangeListener
import org.eclipse.core.resources.IResourceDelta
import org.eclipse.core.resources.IResourceDeltaVisitor
import org.eclipse.core.resources.ResourcesPlugin
import org.scalaide.core.internal.project.ScalaProject

/**
 * Convenient way to react to changes that happen to
 * files in eclipse projects in the workspace.
 */
object FileChangeObserver {

  /**
   * Starts an IResourceChangeListener that reacts to file changes. You
   * are responsible for stopping the listener when appropriate using the
   * stop method on Observing.
   */
  def apply(p: ScalaProject)
           (onChanged: IFile => Unit = _ => (),
            onRemoved: IFile => Unit = _ => (),
            onAdded: IFile => Unit = _ => ()): Observing = {

    val observer = new FileChangeObserver.ChangeListener(p, onChanged, onRemoved, onAdded)

    ResourcesPlugin.getWorkspace().addResourceChangeListener(
      observer,
      IResourceChangeEvent.POST_CHANGE)

    new Observing(observer)

  }

  // Hiding the Eclipse implementation here as we don't want the methods that
  // Eclipse needs to leak into the interface of FileChangeObserver.
  private class ChangeListener(
           project: ScalaProject,
         onChanged: IFile => Unit = _ => (),
         onRemoved: IFile => Unit = _ => (),
           onAdded: IFile => Unit = _ => ()) extends IResourceChangeListener with HasLogger {

    import EclipseImplicits._

    override def resourceChanged(event: IResourceChangeEvent): Unit = {
      for {
        ev <- Option(event)
        delta <- Option(ev.getDelta())
      } {
        delta.accept { (delta: IResourceDelta) =>
          val resource = delta.getResource
          if (resource.getType == IResource.FILE) {
            if (resource.isInstanceOf[IFile]) {
              val file = resource.asInstanceOf[IFile]
              if (file.getProject().equals(project.underlying)) {
                delta.getKind match {
                  case IResourceDelta.ADDED   => onAdded(file)
                  case IResourceDelta.REMOVED => onRemoved(file)
                  case IResourceDelta.CHANGED => onChanged(file)
                }
              }
            } else eclipseLog.error(s"Expected resource to be of type IFile, found ${resource.getClass}")
            false
          } else true
        }
      }
    }
  }
}