package org.scala.tools.eclipse.search

import org.junit.runner.RunWith
import org.junit.runners.Suite


@RunWith(classOf[Suite])
@Suite.SuiteClasses(Array(
  classOf[OccurrenceCollectorTest],
  classOf[LuceneIntegrationTest],
  classOf[IndexTest],
  classOf[UsingTest]
))
class TestsSuite {}
