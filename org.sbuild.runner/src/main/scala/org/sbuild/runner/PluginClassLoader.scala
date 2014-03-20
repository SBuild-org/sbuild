package org.sbuild.runner

import java.util.jar.JarInputStream
import java.net.URLClassLoader
import java.net.URL
import org.sbuild.Logger
import org.sbuild.Constants
import org.sbuild.ProjectConfigurationException
import org.sbuild.SBuildException
import java.io.File
import org.sbuild.Project
import scala.util.Try
import scala.util.Success
import java.util.concurrent.ConcurrentHashMap

object LoadablePluginInfo {
  case class PluginClasses(instanceClass: String, factoryClass: String, version: String) {
    def name: String = s"${instanceClass}-${version}"
  }
}

class LoadablePluginInfo(val files: Seq[File], raw: Boolean) {

  import LoadablePluginInfo._

  private[this] val log = Logger[LoadablePluginInfo]

  lazy val urls: Seq[URL] = files.map(_.toURI().toURL())

  val (
    exportedPackages: Option[Seq[String]],
    dependencies: Seq[String],
    // (instanceClassName, factoryClassName, version)
    pluginClasses: Seq[PluginClasses],
    sbuildVersion: Option[String]
    ) = if (raw || files.isEmpty) (None, Seq(), Seq(), None) else {

    // TODO: Support more than one url, see also https://github.com/SBuild-org/sbuild/issues/175
    val manifest = Option(new JarInputStream(urls.head.openStream()).getManifest())

    val exportedPackages: Option[Seq[String]] = manifest.flatMap { m =>
      m.getMainAttributes().getValue(Constants.ManifestSBuildExportPackage) match {
        case null => None
        //      log.warn("Plugin does not define Manifest Entry " + Constants.SBuildPluginExportPackage)
        //      Seq()
        case v => Some(v.split(",").map(_.trim))
      }
    }

    val dependencies: Seq[String] = manifest.toSeq.flatMap { m =>
      m.getMainAttributes().getValue(Constants.ManifestSBuildClasspath) match {
        case null => Seq()
        case c =>
          // TODO, support more featureful splitter, because we want to support the whole schemes
          c.split(",").toSeq.map(_.trim)
      }
    }

    val pluginClasses: Seq[PluginClasses] = manifest.toSeq.flatMap { m =>
      m.getMainAttributes().getValue(Constants.ManifestSBuildPlugin) match {
        case null => Seq()
        case p =>
          p.split(",").toSeq.map { entry =>
            entry.split("=", 2) match {
              case Array(instanceClassName, factoryClassNameAndVersoin) =>
                val fnv = factoryClassNameAndVersoin.split(";version=", 2)
                val factoryClassName = fnv(0)
                val version =
                  if (fnv.size == 1) "0.0.0"
                  else {
                    val versionString = fnv(1).trim
                    if (versionString.startsWith("\"") && versionString.endsWith("\"")) {
                      versionString.substring(1, versionString.size - 1)
                    } else versionString
                  }
                PluginClasses(instanceClassName.trim, factoryClassName.trim, version)
              case _ =>
                // FIXME: Change exception to new plugin exception
                val ex = new ProjectConfigurationException("Invalid plugin entry: " + entry)
                throw ex
            }
          }
      }

    }

    val sbuildVersion = manifest.flatMap { m =>
      m.getMainAttributes().getValue(Constants.ManifestSBuildVersion) match {
        case null => None
        case v => Some(v.trim)
      }
    }

    (exportedPackages, dependencies, pluginClasses, sbuildVersion)
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

  override def toString() = getClass.getSimpleName +
    "(files=" + files +
    ",raw=" + raw +
    ")"

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

  if (ParallelClassLoader.isJava7) {
    ClassLoader.registerAsParallelCapable()
  }

  private[this] val classLockMap = new ConcurrentHashMap[String, Any]

  protected def getClassLock(className: String): AnyRef =
    ParallelClassLoader.withJava7 { () => getClassLoadingLock(className) }.getOrElse {
      val newLock = new Object()
      classLockMap.putIfAbsent(className, newLock) match {
        case null => newLock
        case lock => lock.asInstanceOf[AnyRef]
      }
    }

  private[this] val log = Logger[PluginClassLoader]

  log.debug(s"Init PluginClassLoader (id: ${System.identityHashCode(this)}) for ${pluginInfo.urls}")

  val pluginClassLoaders: Seq[PluginClassLoader] = childTrees.collect {
    case cpTree if cpTree.pluginInfo.isDefined => new PluginClassLoader(project, cpTree.pluginInfo.get, cpTree.childs, this)
  }

  // register found plugin classes
  pluginInfo.pluginClasses.map {
    case LoadablePluginInfo.PluginClasses(instanceClassName, factoryClassName, version) =>
      project.registerPlugin(instanceClassName, factoryClassName, version, this)
  }

  def loadPluginClass(className: String): Class[_] = getClassLock(className).synchronized {
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
        "\"), it is not exported with " + Constants.ManifestSBuildExportPackage + ".")

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
