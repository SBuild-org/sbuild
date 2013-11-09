package de.tototec.sbuild.runner

import java.util.jar.JarInputStream
import java.net.URLClassLoader
import java.net.URL
import de.tototec.sbuild.Logger
import de.tototec.sbuild.Constants

class PluginClassLoader(pluginUrl: URL, parent: ProjectClassLoader)
    extends URLClassLoader(Array(pluginUrl), parent) {

  //  ClassLoader.registerAsParallelCapable()

  private[this] val log = Logger[PluginClassLoader]

  lazy val allowedPackageNames: Seq[String] = {
    val manifest = new JarInputStream(pluginUrl.openStream()).getManifest()
    val value = manifest.getMainAttributes().getValue(Constants.SBuildPluginExportPackage)
    value match {
      case null =>
        log.warn("Plugin does not define Manifest Entry " + Constants.SBuildPluginExportPackage)
        Seq()
      case v => v.split(",").map(_.trim)
    }
  }

  def checkClassNameInExported(className: String): Boolean = {
    val parts = className.split("\\.").toList.reverse

    def removeNonPackageParts(parts: List[String]): List[String] = parts match {
      case cn :: p if (cn.headOption.filter(_.isLower).isEmpty) => removeNonPackageParts(p)
      case p => p
    }
    val packageName = removeNonPackageParts(parts.tail).reverse.mkString(".")
    allowedPackageNames.contains(packageName)
  }

  def loadPluginClass(className: String): Class[_] = { // getClassLoadingLock(className).synchronized {
    // First, check if the class has already been loaded
    findLoadedClass(className) match {
      case loadedClass: Class[_] => loadedClass
      case null =>
        if (checkClassNameInExported(className)) findClass(className)
        else throw new ClassNotFoundException(className)
    }
  }

  // Find the class, but restrict to what is exported
  //  override protected def findClass(className: String): Class[_] = ???

  override def loadClass(className: String): Class[_] = parent.loadClass(className)
  override protected def loadClass(className: String, resolve: Boolean): Class[_] = parent.loadClass(className)

}

