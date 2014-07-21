package org.scala.tools.eclipse.search
package ui

import scala.collection.mutable
import org.scalaide.logging.HasLogger
import org.scalaide.util.internal.Utils._
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.Status
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.jface.viewers.ITreeContentProvider
import org.eclipse.jface.viewers.TreeViewer
import org.eclipse.jface.viewers.Viewer
import org.eclipse.swt.widgets.Display
import org.scala.tools.eclipse.search.ErrorHandlingOption
import org.scala.tools.eclipse.search.SearchPlugin
import org.scala.tools.eclipse.search.searching.Certain
import org.scala.tools.eclipse.search.searching.Confidence
import org.scala.tools.eclipse.search.searching.TypeHierarchyNode
import org.scala.tools.eclipse.search.searching.EvaluatedNode
import org.scala.tools.eclipse.search.searching.EvaluatingNode
import org.scala.tools.eclipse.search.searching.LeafNode
import org.scalaide.core.api.ScalaProject
import org.scala.tools.eclipse.search.searching.Scope

/**
 * Used by the TypeHierarchyView to produce the content needed for the type hierarchy
 * tree viewer.
 *
 * This is able to lazily produce the sub-type hierarchy of a given root TypeEntity, that is,
 * it will display the direct sub-types initially and when the user expands a node in the
 * tree it will then find the sub-types of that entity.
 *
 * Given that we're implementing an Eclipse interface here we don't have much type-safty.
 * We expect that the input type, i.e. the type passed to `inputChanged` is an instance of
 * TypeEntity which represents the root of the type hierarchy.
 *
 */
class TypeHierarchyTreeContentProvider(
    viewer: TreeViewer,
    onExpandNode: (TypeEntity, Scope, IProgressMonitor, Confidence[TypeEntity] => Unit) => Unit) extends ITreeContentProvider with HasLogger {

  private val lock = new Object

  /* This is the "input" that we should use to produce the content for the
   * hierarchy. The input is a tuple containing the given root entity and
   * the scope we should use to search for sub-types in.
   */
  private type InputType = (TypeEntity, Scope)

  /* This is the base type for the `nodes` in the tree. That is, instances
   * of this type is used in the `getElements`, `getChildren` and
   * `hasChildren` methods. As such, this is also the type that is
   * passed along to the TypeHierarchyTreeLabelProvider by Eclipse.
   */
  private type ElementType = TypeHierarchyNode

  /* This can potentially be accessed concurrently: By the UI thread when
   * drawing the tree and by the Job started by `findInBackground`
   * when finding sub types; multiple Jobs could be running at the
   * same time if the user expands nodes quickly.
   *
   * @note Guarded by `lock`.
   */
  private val cache = new mutable.HashMap[TypeEntity, Seq[ElementType]]

  /* Needs to be volatile as it's accessed in many different threads (the view
   * thread in dispose and inputChanged) and any of the background jobs started
   * in `findInBackground`.
   */
  @volatile private var input: InputType = _

  override def dispose() = lock.synchronized {
    input = null
    cache.clear()
  }

  // Invoked when there's a new root for the hierarchy.
  override def inputChanged(viewer: Viewer, oldInput: Object, newInput: Object) = {
    lock.synchronized {
      cache.clear()
      if (newInput == null) {
        input == null
      } else {
        // newInput.asInstanceOfOpt[InputType] returns null for some reason.
        val (x1: TypeEntity, x2: Scope) = newInput
        input = (x1, x2)
      }
    }
    viewer.refresh()
  }

  // Find the initial set of children based on the input passed in
  // inputChanged.
  override def getElements(inputElement: Object): Array[Object] = {
    // inputElement.asInstanceOfOpt[InputType] returns None for some reason.
    val (x1: TypeEntity, _) = inputElement
    getChildren(EvaluatedNode(Certain(x1)))
  }

  // Invoked when expanding nodes.
  override def getChildren(parentElement: Object): Array[Object] = {
    (for {
      element <- parentElement.asInstanceOfOpt[ElementType] onEmpty
        logger.debug(s"getChildren() got unexpected type $parentElement")
    } yield {
      getChildrenOf(element).toArray.asInstanceOf[Array[Object]]
    }) getOrElse Array[Object]()
  }

  override def getParent(element: Object): Object = null

  override def hasChildren(element: Object): Boolean = lock.synchronized {
    // By default we return true as this means that the node
    // hasn't been expanded yet.
    (for {
      entity <- element.asInstanceOfOpt[ElementType] onEmpty
        logger.debug(s"Got unexpected element '$element'")
    } yield entity match {
      case EvaluatedNode(hit) => cache.get(hit.value) map (!_.isEmpty) getOrElse true
      case _ => false
    }) getOrElse false
  }

  // Get the cached subtypes or start a jobs that finds them.
  private def getChildrenOf(entity: ElementType): Seq[ElementType] = lock.synchronized {
    entity match {
      case EvaluatedNode(hit) => cache.get(hit.value) getOrElse {
        findInBackground(hit.value)
        List(EvaluatingNode)
      }
      case _ => Nil: List[ElementType]
    }
  }

  /* Start a background job that finds all the sub-types of the given
   * entity. Once it's done it will update the cache and invoke refresh.
   */
  private def findInBackground(entity: TypeEntity): Unit = {
    // Store a reference to the root of the hierarchy when the job is started.
    // this is needed to make sure that the view hasn't been closed or
    // a new hierarchy has been created since the job was started.
    val oldRoot = input
    val scope = input._2
    // Given that this is invoked, we assume that the map doesn't have
    // an entry for this `entity` yet.
    val job = new Job(s"Finding subtypes of ${entity.name}") {
      override def run(monitor: IProgressMonitor): IStatus = {
        var found: Seq[ElementType] = Nil
        onExpandNode(entity, scope, monitor, (hit: Confidence[TypeEntity]) => {
          if (oldRoot ne input) {
            this.cancel()
          } else {
            found = EvaluatedNode(hit) +: found
          }
        })
        lock.synchronized {
          // make sure it's still the same root.
          if (oldRoot eq input) {
            if (found.isEmpty) cache.put(entity, Seq(LeafNode))
            else cache.put(entity, found)
          }
        }
        // We have to run refresh on the UI thread!
        Display.getDefault().asyncExec(new Runnable() {
          def run(): Unit = {
            // Only refresh if the view hasn't been disposed.
            if (input != null) {
              viewer.refresh()
            }
          }
        })
        Status.OK_STATUS
      }
    }
    job.setPriority(Job.SHORT)
    job.schedule
  }

}