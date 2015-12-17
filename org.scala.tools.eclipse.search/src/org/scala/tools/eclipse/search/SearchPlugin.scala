package org.scala.tools.eclipse.search

import scala.Array.canBuildFrom
import scala.Option.option2Iterable
import org.scalaide.logging.HasLogger
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.ui.plugin.AbstractUIPlugin
import org.osgi.framework.BundleContext
import org.scala.tools.eclipse.search.indexing.Index
import org.scala.tools.eclipse.search.indexing.SourceIndexer
import org.scalaide.core.IScalaPlugin
import org.scala.tools.eclipse.search.searching.Finder
import org.scala.tools.eclipse.search.ui.DialogErrorReporter

object SearchPlugin extends HasLogger {

  // Only expose the Finder API.
  @volatile var finder: Finder = _
  @volatile private var plugin: SearchPlugin = _

  final val PLUGIN_ID  = "org.scala.tools.eclipse.search"
  def apply() = plugin
}

class SearchPlugin extends AbstractUIPlugin with HasLogger {

  private final val INDEX_DIR_NAME = "lucene-indices"

  @volatile private var indexManager: IndexJobManager = _

  override def start(context: BundleContext) {
    super.start(context)
    SearchPlugin.plugin = this
    val reporter = new DialogErrorReporter
    val index = new Index {
      override val base = getStateLocation().append(INDEX_DIR_NAME)
    }
    val indexer = new SourceIndexer(index) 
   
 
    indexManager = new IndexJobManager(indexer)
    indexManager.startup()

    SearchPlugin.finder = new Finder(index, reporter)

    val root = ResourcesPlugin.getWorkspace().getRoot()

    root.getProjects().map(Option.apply).flatten.foreach { proj =>
      IScalaPlugin().asScalaProject(proj).foreach { sp =>
        indexManager.startIndexing(sp.underlying)
      }
    }

  }

  override def stop(context: BundleContext) {
    super.stop(context)
    indexManager.shutdown()
    indexManager = null
    SearchPlugin.finder = null
    SearchPlugin.plugin = null
  }

}