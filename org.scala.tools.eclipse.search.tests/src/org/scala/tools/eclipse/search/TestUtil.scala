package org.scala.tools.eclipse.search

import java.io.File
import java.io.FileOutputStream
import org.eclipse.core.resources.IProject
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

trait TestUtil {

  def mkPath(xs: String*): String = {
    xs.mkString(File.separator)
  }

  def addSourceFile(project: IProject)(name: String, contents: String) = {
    val folder = project.getFolder("src")
    val file = folder.getFile(name)
    if (!folder.exists())
      folder.create(IResource.NONE, true, null)
    if (!file.exists()) {
      val source = new ByteArrayInputStream(contents.getBytes())
      file.create(source, IResource.FORCE, null)
   }
  }

  def deleteSourceFile(project: IProject)(name: String) = {
    val folder = project.getFolder("src")
    val file = folder.getFile(name)
    if (folder.exists() && file.exists())
      file.delete(true, false, null)
  }

  def addContent(project: IProject)(name: String, contents: String) = {
    val folder = project.getFolder("src")
    val file = folder.getFile(name)
    if (folder.exists() && file.exists()) {
      val source = new ByteArrayInputStream(contents.getBytes())
      file.appendContents(source, IResource.FORCE, null)
    }
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