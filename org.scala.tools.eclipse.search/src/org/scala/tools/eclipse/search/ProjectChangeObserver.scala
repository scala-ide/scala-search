package org.scala.tools.eclipse.search

import org.scalaide.logging.HasLogger
import org.eclipse.core.resources.IProject
import org.eclipse.core.resources.IResourceChangeEvent
import org.eclipse.core.resources.IResourceChangeListener
import org.eclipse.core.resources.IResourceDelta
import org.eclipse.core.resources.IResourceDeltaVisitor
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.resources.IResource
import org.scalaide.util.Utils.WithAsInstanceOfOpt
import org.scalaide.core.SdtConstants


/**
 * Convenient way to react to changes that happen to project in the Eclipse
 * workspace.
 *
 * A ProjectChangeObserver is not associated with a single project but observes
 * changes to all projects in the workspace.
 *
 * The `onNewScalaProject` is invoked when the Scala nature is added to the description
 * of a project. This usually happens after the creation of new scala projects, hence
 * the name.
 */
object ProjectChangeObserver {

  /**
   * Starts an IResourceChangeListener that reacts to project state changes. You
   * are responsible for stopping the listener when appropriate using the stop
   * method on Observing.
   */
  def apply(   onOpen: IProject => Unit = _ => (),
              onClose: IProject => Unit = _ => (),
             onDelete: IProject => Unit = _ => (),
    onNewScalaProject: IProject => Unit = _ => ()): Observing = {

    val observer = new ChangeListener(onOpen, onClose, onDelete, onNewScalaProject)

    ResourcesPlugin.getWorkspace().addResourceChangeListener(
      observer,
      IResourceChangeEvent.POST_CHANGE | IResourceChangeEvent.PRE_DELETE)

    new Observing(observer)
  }

  // Hidding the Eclipse implementation here as we don't want the methods that
  // Eclipse needs to leak into the interface of ProjectChangeObserver.
  private class ChangeListener(
               onOpen: IProject => Unit = _ => (),
              onClose: IProject => Unit = _ => (),
             onDelete: IProject => Unit = _ => (),
    onNewScalaProject: IProject => Unit = _ => ()) extends IResourceChangeListener with HasLogger {

    import EclipseImplicits._

    override def resourceChanged(event: IResourceChangeEvent): Unit = {
      Option(event).foreach { ev =>
        event.getType match {
          case IResourceChangeEvent.POST_CHANGE => event.getDelta.accept { (delta: IResourceDelta) =>
            if (delta.getResource().isInstanceOf[IProject]) {
              val project = delta.getResource().asInstanceOf[IProject]
              if ((delta.getFlags & IResourceDelta.OPEN) != 0) {
                if (project.isOpen()) {
                  onOpen(project)
                }
                else {
                  onClose(project)
                }
              } else if ((delta.getFlags & IResourceDelta.DESCRIPTION ) != 0) {
                // When Scala projects are created their description is set to have the
                // Scala nature. That produces an event that is separate and comes after
                // the ADDED and OPEN events.
                if(project.hasNature(SdtConstants.NatureId) && project.isOpen)
                  onNewScalaProject(project)
              }
              false
            } else {
              // The resource delta node wasn't a project, so descend into the
              // tree (most likely it was IWorkspaceRoot).
              // See http://www.eclipse.org/articles/Article-Resource-deltas/resource-deltas.html
              true
            }
          }
          case IResourceChangeEvent.PRE_DELETE => {
            ev.getResource.asInstanceOfOpt[IProject].foreach(onDelete)
          }
        }
      }
    }
  }
}
