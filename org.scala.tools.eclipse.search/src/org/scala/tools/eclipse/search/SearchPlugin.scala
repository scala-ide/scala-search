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
import org.scala.tools.eclipse.search.searching.Finder
import org.scala.tools.eclipse.search.ui.DialogErrorReporter

object SearchPlugin extends HasLogger {

  // Only expose the Finder API.
  @volatile var finder: Finder = _

  final val PLUGIN_ID  = "org.scala.tools.eclipse.search"
}

class SearchPlugin extends AbstractUIPlugin with HasLogger {

  private final val INDEX_DIR_NAME = "lucene-indices"

  @volatile private var indexManager: IndexJobManager = _

  override def start(context: BundleContext) {
    super.start(context)

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
      ScalaPlugin.plugin.asScalaProject(proj).foreach { sp =>
        indexManager.startTrackingChanges(sp.underlying)
      }
    }

  }

  override def stop(context: BundleContext) {
    super.stop(context)
    indexManager.shutdown()
    indexManager = null
    SearchPlugin.finder = null
  }

}