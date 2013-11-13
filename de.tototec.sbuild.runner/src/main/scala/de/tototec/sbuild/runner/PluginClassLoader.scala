package de.tototec.sbuild.runner

import java.util.jar.JarInputStream
import java.net.URLClassLoader
import java.net.URL
import de.tototec.sbuild.Logger
import de.tototec.sbuild.Constants
import de.tototec.sbuild.ProjectConfigurationException
import de.tototec.sbuild.SBuildException
import java.io.File

class LoadablePluginInfo(val files: Seq[File], raw: Boolean) {

  private[this] val log = Logger[LoadablePluginInfo]

  lazy val urls: Seq[URL] = files.map(_.toURI().toURL())

  private[this] lazy val manifest =
    if (raw || files.isEmpty) None
    else Some(new JarInputStream(urls.head.openStream()).getManifest())
    

  lazy val exportedPackages: Option[Seq[String]] = manifest.flatMap { m =>
    m.getMainAttributes().getValue(Constants.SBuildPluginExportPackage) match {
      case null => None
      //      log.warn("Plugin does not define Manifest Entry " + Constants.SBuildPluginExportPackage)
      //      Seq()
      case v => Some(v.split(",").map(_.trim))
    }
  }

  lazy val singleton: Boolean = manifest.map { m =>
    m.getMainAttributes().getValue(Constants.SBuildPluginMultipleInstances) match {
      case null => true
      case v => v.trim.toLowerCase() match {
        case "true" => false
        case "false" => true
        case _ =>
          log.warn("Invalid value for manifest entry \"" + Constants.SBuildPluginMultipleInstances + "\" detected: " + v)
          true
      }
    }
  }.getOrElse(true)

}

object PluginClassLoader {
  private object InnerRequestGuard {
    private[this] val _innerRequests: ThreadLocal[Seq[(PluginClassLoader, String)]] = new ThreadLocal

    private[this] def innerRequests: Seq[(PluginClassLoader, String)] = {
      if (_innerRequests.get() == null) _innerRequests.set(Seq())
      _innerRequests.get
    }
    private[this] def innerRequests_=(innerRequests: Seq[(PluginClassLoader, String)]): Unit =
      _innerRequests.set(innerRequests)

    def isInner(classLoader: PluginClassLoader, className: String): Boolean = innerRequests.exists { case (cl, cn) => cl == classLoader && cn == className }
    def addInner(classLoader: PluginClassLoader, className: String) = innerRequests +:= classLoader -> className
    def removeInner(classLoader: PluginClassLoader, className: String) = innerRequests = innerRequests.filter { case (cl, cn) => cl == classLoader && cn == className }
    def within[T](classLoader: PluginClassLoader, className: String)(f: => T): T = {
      addInner(classLoader, className)
      try f
      finally InnerRequestGuard.removeInner(classLoader, className)
    }
  }
}

class PluginClassLoader(pluginInfo: LoadablePluginInfo, parent: ClassLoader)
    extends URLClassLoader(pluginInfo.urls.toArray, parent) {

  import PluginClassLoader._

  //  ClassLoader.registerAsParallelCapable()

  private[this] val log = Logger[PluginClassLoader]

  def checkClassNameInExported(className: String): Boolean = pluginInfo.exportedPackages match {
    case None => true
    case Some(ep) =>
      val parts = className.split("\\.").toList.reverse

      def removeNonPackageParts(parts: List[String]): List[String] = parts match {
        case cn :: p if (cn.headOption.filter(_.isLower).isEmpty) => removeNonPackageParts(p)
        case p => p
      }
      val packageName = removeNonPackageParts(parts.tail).reverse.mkString(".")
      ep.contains(packageName)
  }

  def loadPluginClass(className: String): Class[_] = { // getClassLoadingLock(className).synchronized {
    val loadable = if (InnerRequestGuard.isInner(this, className)) true else checkClassNameInExported(className)

    // First, check if the class has already been loaded
    val loadedClass = findLoadedClass(className) match {
      case loadedClass: Class[_] => loadedClass
      case null =>
        findClass(className)
    }

    if (loadable) loadedClass
    else if (loadedClass != null) throw new ClassNotFoundException(
      className + "\nAlthough the class is contained in this plugin (\"" + pluginInfo.urls.mkString(",") +
        "\"), it is not exported with " + Constants.SBuildPluginExportPackage + ".")
    else throw new ClassNotFoundException(className)

  }

  // Find the class, but restrict to what is exported
  //  override protected def findClass(className: String): Class[_] = ???

  override protected def loadClass(className: String, resolve: Boolean): Class[_] = {
    InnerRequestGuard.within(this, className) { parent.loadClass(className) }
  }

  override def toString: String = getClass.getSimpleName +
    "(pluginInfo=" + pluginInfo +
    ",parent=" + parent + ")"

}
