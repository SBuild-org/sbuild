package de.tototec.sbuild.runner

import java.net.URLClassLoader
import java.net.URL

/**
 * An URL classloader that allows to add additional URLs after construction.
 */
class SBuildURLClassLoader(urls: Array[URL], parent: ClassLoader) extends URLClassLoader(urls, parent) {

  //  println("urls: " + urls.mkString(","))

  //  override protected def findClass(className: String): Class[_] = {
  //    try {
  //      SBuildRunner.verbose("About to find class: " + className)
  //      val res = super.findClass(className)
  //      SBuildRunner.verbose("Found class: " + res)
  //      res
  //    } catch {
  //      case e =>
  //        SBuildRunner.verbose("Caught a: " + e)
  //        throw e
  //    }
  //  }

  override def addURL(url: URL) {
    SBuildRunner.verbose("About to add an URL: " + url)
    super.addURL(url)
  }

}