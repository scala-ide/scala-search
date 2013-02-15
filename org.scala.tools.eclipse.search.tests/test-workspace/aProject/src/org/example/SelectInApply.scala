package org.example

class Foo {
  def bar(str: String) = str
}

class Bar {
  def x = "test"
}

object ScalaClass {
  val foo = new Foo
  val bar = new Bar
  foo.bar(bar.x)
}