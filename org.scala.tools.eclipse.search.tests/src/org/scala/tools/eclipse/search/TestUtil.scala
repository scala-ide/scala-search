package org.scala.tools.eclipse.search

import java.io.File
import java.io.FileOutputStream
import org.eclipse.core.resources.IResource
import java.io.ByteArrayInputStream
import java.util.concurrent.CountDownLatch
import org.mockito.Mockito.when
import org.mockito.stubbing.Answer
import org.mockito.invocation.InvocationOnMock
import org.mockito.verification.VerificationMode
import org.mockito.ArgumentMatcher
import org.eclipse.core.resources.IFile
import org.eclipse.core.runtime.IPath
import java.util.concurrent.TimeUnit
import scala.tools.eclipse.ScalaProject
import scala.tools.eclipse.ScalaPlugin
import org.eclipse.core.runtime.Path

trait TestUtil {

  // JDT usees 10 seconds as the max for events. We use latches so
  // we should never have to wait 10 seconds unless that test is
  // actually broken.
  val EVENT_DELAY = 10

  def mkPath(xs: String*): String = {
    xs.mkString(File.separator)
  }

  object mocks {

    object args {

      def anyInstance[A](implicit m: Manifest[A]) = new ArgumentMatcher[A] {
        def matches(o: Object): Boolean = o match {
          case x: A => true
          case _ => false
        }
      }

      def fileNamed(name: String) = new ArgumentMatcher[IFile] {
        def matches(o: Object): Boolean = {
          if (o.isInstanceOf[IFile]) {
            val ifile = o.asInstanceOf[IFile]
            ifile.getName() == name
          } else false
        }
      }

      def pathEndingWith(name: String) = new ArgumentMatcher[IPath] {
        def matches(o: Object): Boolean = {
          if (o.isInstanceOf[IPath]) {
            val ifile = o.asInstanceOf[IPath]
            ifile.lastSegment() == name
          } else false
        }
      }
    }
  }

}