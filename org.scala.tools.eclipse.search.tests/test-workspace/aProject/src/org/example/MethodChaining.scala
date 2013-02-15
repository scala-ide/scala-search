package org.example

class MethodChaining {
  
  def foo() = this
  def bar() = this
  
}

object MethodChaining {

  def method() = {
    val x new MethodChaining()
    x.foo().bar()
  }
  
}