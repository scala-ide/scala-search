package org.example

class ScalaClass {
  def method: String = {
    val s2 = methodTwo(methodOne)
    methodThree(s1)(methodTwo(s2))
  }
}

object ScalaClass {
  def methodOne = "Test"
  def methodTwo(s: String) = s
  def methodThree(s: String)(s2: String) = s + s2
}