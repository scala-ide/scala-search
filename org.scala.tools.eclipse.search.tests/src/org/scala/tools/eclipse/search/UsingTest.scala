package org.scala.tools.eclipse.search

import java.io.Closeable
import java.io.IOException

import scala.util.Try
import scala.util.control.{Exception => Ex}

import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.when

class UsingTest {

  private class ExpectedException(msg: String) extends Exception(msg)

  def anonymousResource: Closeable = mock(classOf[Closeable])

  def exceptionThrowingResource: Closeable = {
    val resource = mock(classOf[Closeable])
    when(resource.close()).thenThrow(new IOException())
    resource
  }

  // Make sure the exceptions are handled as expected

  @Test def shouldSwallowExceptionsOnClose() {
    using(exceptionThrowingResource) { _ => () }
  }

  @Test(expected = classOf[ExpectedException])
  def shouldPropagateExceptionsOfBodyByDefault() {
    using(anonymousResource) { _ =>
      throw new ExpectedException("This should propagate")
    }
  }

  @Test def canCatchExceptions() {
    using(anonymousResource, handlers = Ex.ignoring(classOf[ExpectedException])) { _ =>
      throw new ExpectedException("This shouldn't propagate")
    }
  }

  @Test def canCatchExceptionsOnResourceOpen() {
    lazy val r: Closeable = throw new ExpectedException("This shouldn't propagate")
    using(r, handlers = Ex.ignoring(classOf[ExpectedException])) { _ => }
  }

  // Make sure we always close the resource

  @Test def shouldCloseOnSuccess() {
    val resource = anonymousResource
    using(resource) { _ => () }
    verify(resource).close()
  }

  @Test def closesWhenCatches() {
    val resource = anonymousResource
    using(resource, handlers = Ex.ignoring(classOf[ExpectedException])) { _ =>
      throw new ExpectedException("This shouldn't propagate")
    }
    verify(resource).close()
  }

  @Test def closesWhenPropagates() {
    val resource = anonymousResource
    Try {
      using(resource) { _ =>
        throw new ExpectedException("This should propagate")
      }
    }
    verify(resource).close()
  }

}