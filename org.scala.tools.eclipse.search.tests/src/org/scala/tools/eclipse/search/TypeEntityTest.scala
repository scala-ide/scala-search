package org.scala.tools.eclipse.search

import org.junit.Assert.assertEquals
import org.junit.Test

class TypeEntityTest {

  @Test
  def testToLongDisplayName() {
    val testCases = Array(
      ("a.b.Foo", "a.b.Foo"),
      ("Foo.type", "Foo"),
      ("type.Bar", "type.Bar"),
      ("a.type.X.type", "a.type.X"),
      ("тип", "тип"),
      ("verrückter.type.тип.type", "verrückter.type.тип"))

    testCases.foreach { case (input, expected) =>
        assertEquals(expected, TypeEntity.toLongDisplayName(input))
    }
  }
}
