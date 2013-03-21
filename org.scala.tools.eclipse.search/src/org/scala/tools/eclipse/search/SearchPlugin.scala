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

  @volatile private var config: Index with SourceIndexer with IndexJobManager with Lifecycle = _

  override def start(context: BundleContext) {
    super.start(context)

    config = new Index with SourceIndexer with IndexJobManager with Lifecycle {
      override val base = getStateLocation().append(INDEX_DIR_NAME)
    }
    config.startup()

    val root = ResourcesPlugin.getWorkspace().getRoot()

    root.getProjects().map(Option.apply).flatten.foreach { proj =>
      ScalaPlugin.plugin.asScalaProject(proj).foreach { sp =>
        config.startIndexing(sp.underlying)
      }
    }

  }

  override def stop(context: BundleContext) {
    super.stop(context)
    config.shutdown()
    config = null
  }

}