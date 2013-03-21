package org.scala.tools.eclipse.search

import org.junit.runner.RunWith
import org.junit.runners.Suite

import org.scala.tools.eclipse.search.jobs._
import org.scala.tools.eclipse.search.indexing._


@RunWith(classOf[Suite])
@Suite.SuiteClasses(Array(
  classOf[OccurrenceCollectorTest],
  classOf[LuceneIntegrationTest],
  classOf[IndexTest],
  classOf[UsingTest],
  classOf[IndexJobManagerTest],
  classOf[ProjectIndexJobTest],
  classOf[ProjectChangeObserverTest]
))
class TestsSuite {}
