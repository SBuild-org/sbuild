package de.tototec.sbuild.runner

import java.net.URLClassLoader
import java.net.URL
import de.tototec.sbuild.Logger

/**
 * An URL classloader that allows to add additional URLs after construction.
 */
class SBuildURLClassLoader(urls: Array[URL], parent: ClassLoader) extends URLClassLoader(urls, parent) {

  private[this] val log = Logger[SBuildURLClassLoader]

  //    println("urls: " + urls.mkString(","))

  override def loadClass(className: String, resolve: Boolean): Class[_] = {
    // First, check if the class has already been loaded
    val loadedClass = findLoadedClass(className) match {
      case c: Class[_] => c
      case _ =>
        if (className.startsWith("scala.tools.ant.")) {
          log.debug("Force loading of scala ant support class '" + className + "' from project classpath, even if they are bundled with the compiler and therefore available in SBuildRunner classpath.")
          findClass(className)
        } else {
          super.loadClass(className, resolve)
        }
    }

    if (resolve) {
      resolveClass(loadedClass);
    }
    return loadedClass;
  }

  //  override protected def findClass(className: String): Class[_] = {
  //    try {
  //      val res = super.findClass(className)
  //      SBuildRunner.verbose("Found class: " + className)
  //      res
  //    } catch {
  //      case e =>
  //        SBuildRunner.verbose("Could not find class: " + className)
  //        throw e
  //    }
  //  }

  override def addURL(url: URL) {
    log.debug("About to add an URL: " + url)
    super.addURL(url)
  }

}