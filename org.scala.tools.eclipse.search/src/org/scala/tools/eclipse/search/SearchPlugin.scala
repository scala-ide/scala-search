package org.scala.tools.eclipse.search

import scala.Array.canBuildFrom
import scala.Option.option2Iterable
import scala.tools.eclipse.logging.HasLogger
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.ui.plugin.AbstractUIPlugin
import org.osgi.framework.BundleContext
import org.scala.tools.eclipse.search.indexing.Index
import org.scala.tools.eclipse.search.indexing.SourceIndexer
import scala.tools.eclipse.ScalaPlugin

object SearchPlugin extends HasLogger {
  final val PLUGIN_ID  = "org.scala.tools.eclipse.search"
}

class SearchPlugin extends AbstractUIPlugin with HasLogger {

  private final val INDEX_DIR_NAME = "lucene-indices"

  @volatile private var indexManager: IndexJobManager = _

  override def start(context: BundleContext) {
    super.start(context)

    val index = new Index with SourceIndexer {
      override val base = getStateLocation().append(INDEX_DIR_NAME)
    }
    indexManager = new IndexJobManager(index)
    indexManager.startup()

    val root = ResourcesPlugin.getWorkspace().getRoot()

    root.getProjects().map(Option.apply).flatten.foreach { proj =>
      ScalaPlugin.plugin.asScalaProject(proj).foreach { sp =>
        indexManager.startIndexing(sp.underlying)
      }
    }

  }

  override def stop(context: BundleContext) {
    super.stop(context)
    indexManager.shutdown()
    indexManager = null
  }

}