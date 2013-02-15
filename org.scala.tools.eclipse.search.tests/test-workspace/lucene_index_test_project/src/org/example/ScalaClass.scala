package org.example

class ScalaClass {
  def method: String = {
    val s1 = methodOne
    val s2 = methodTwo(s1)
    methodThree(s1)(s2)
  }
}

object ScalaClass {
  def methodOne = "Test"
  def methodTwo(s: String) = s
  def methodThree(s: String)(s2: String) = s + s2
}