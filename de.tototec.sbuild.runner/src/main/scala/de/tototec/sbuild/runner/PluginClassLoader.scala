package de.tototec.sbuild.runner

import java.util.jar.JarInputStream
import java.net.URLClassLoader
import java.net.URL
import de.tototec.sbuild.Logger
import de.tototec.sbuild.Constants
import de.tototec.sbuild.ProjectConfigurationException
import de.tototec.sbuild.SBuildException
import java.io.File
import de.tototec.sbuild.Project
import scala.util.Try
import scala.util.Success

class LoadablePluginInfo(val files: Seq[File], raw: Boolean) {

  private[this] val log = Logger[LoadablePluginInfo]

  lazy val urls: Seq[URL] = files.map(_.toURI().toURL())

  val (
    exportedPackages: Option[Seq[String]],
    dependencies: Seq[String],
    pluginClasses: Seq[(String, String)]
    ) = if (raw || files.isEmpty) (None, Seq(), Seq()) else {

    val manifest = Option(new JarInputStream(urls.head.openStream()).getManifest())

    val exportedPackages: Option[Seq[String]] = manifest.flatMap { m =>
      m.getMainAttributes().getValue(Constants.SBuildPluginExportPackage) match {
        case null => None
        //      log.warn("Plugin does not define Manifest Entry " + Constants.SBuildPluginExportPackage)
        //      Seq()
        case v => Some(v.split(",").map(_.trim))
      }
    }

    val dependencies: Seq[String] = manifest.toSeq.flatMap { m =>
      m.getMainAttributes().getValue(Constants.SBuildPluginClasspath) match {
        case null => Seq()
        case c =>
          // TODO, support more featureful splitter, because we want to support the whole schemes
          c.split(",").toSeq.map(_.trim)
      }
    }

    val pluginClasses: Seq[(String, String)] = manifest.toSeq.flatMap { m =>
      m.getMainAttributes().getValue(Constants.SBuildPlugin) match {
        case null => Seq()
        case p =>
          p.split(",").toSeq.map { entry =>
            entry.split("=", 2) match {
              case Array(instanceClassName, factoryClassName) => instanceClassName -> factoryClassName
              case _ =>
                // FIXME: Change exception to new plugin exception
                val ex = new ProjectConfigurationException("Invalid plugin entry: " + entry)
                throw ex
            }
          }
      }
    }

    (exportedPackages, dependencies, pluginClasses)
  }

  def checkClassNameInExported(className: String): Boolean = exportedPackages match {
    case None => true
    case Some(ep) =>
      val parts = className.split("\\.").toList.reverse

      def removeNonPackageParts(parts: List[String]): List[String] = parts match {
        case cn :: p if (cn.headOption.filter(_.isLower).isEmpty) => removeNonPackageParts(p)
        case p => p
      }
      val packageName = removeNonPackageParts(parts.tail).reverse.mkString(".")
      ep.toIterator.map { export =>
        def matches(packageName: String): Boolean = (export == packageName ||
          (export.endsWith(".*") && packageName.startsWith(export.substring(0, export.length - 2))) ||
          (export.endsWith("*") && packageName.startsWith(export.substring(0, export.length - 1))))

        if (matches(packageName)) Some(true)
        else if (matches("!" + packageName)) Some(false)
        else None
      }.find(_.isDefined) match {
        case Some(Some(exported)) => exported
        case _ => false
      }
  }

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

class PluginClassLoader(project: Project, pluginInfo: LoadablePluginInfo, childTrees: Seq[CpTree], parent: ClassLoader)
    extends URLClassLoader(pluginInfo.urls.toArray, parent) {

  import PluginClassLoader._

  //  ClassLoader.registerAsParallelCapable()

  private[this] val log = Logger[PluginClassLoader]

  val pluginClassLoaders: Seq[PluginClassLoader] = childTrees.collect {
    case cpTree if cpTree.pluginInfo.isDefined => new PluginClassLoader(project, cpTree.pluginInfo.get, cpTree.childs, this)
  }

  // register found plugin classes
  pluginInfo.pluginClasses.map {
    case (instanceClassName, factoryClassName) =>
      project.registerPlugin(instanceClassName, factoryClassName, this)
  }

  def loadPluginClass(className: String): Class[_] = { // getClassLoadingLock(className).synchronized {
    val loadable = if (InnerRequestGuard.isInner(this, className)) true else pluginInfo.checkClassNameInExported(className)

    // First, check if the class has already been loaded
    val loadedClass = findLoadedClass(className) match {
      case loadedClass: Class[_] => loadedClass
      case null =>
        try {
          findClass(className)
        } catch {
          case e: ClassNotFoundException =>
            // log.trace("Couldn't found class before searching in plugins: " + className)
            // we use an iterator to lazily load the class from next CL until we found it.
            pluginClassLoaders.toIterator.map {
              case pluginClassLoader =>
                Try[Class[_]](pluginClassLoader.loadPluginClass(className))
            }.find(_.isSuccess) match {
              case Some(Success(loadedClass)) => loadedClass
              case _ =>
                // log.trace("Couldn't found class after searching in plugins: " + className + " Classloader: " + this)
                throw new ClassNotFoundException(className)
            }
        }
    }

    if (loadable) loadedClass
    else throw new ClassNotFoundException(
      className + "\nAlthough the class is contained in this plugin (\"" + pluginInfo.urls.mkString(",") +
        "\"), it is not exported with " + Constants.SBuildPluginExportPackage + ".")

  }

  override protected def loadClass(className: String, resolve: Boolean): Class[_] = {
    InnerRequestGuard.within(this, className) {
      // All call from child and this will see all classes.
      parent.loadClass(className)
    }
  }

  override def toString: String = getClass.getSimpleName +
    "(pluginInfo=" + pluginInfo +
    ",parent=" + parent + ")"

}
