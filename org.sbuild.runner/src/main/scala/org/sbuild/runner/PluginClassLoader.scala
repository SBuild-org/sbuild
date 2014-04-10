package org.sbuild.runner

import java.net.URLClassLoader

import scala.util.Success
import scala.util.Try

import org.sbuild.Constants
import org.sbuild.Logger
import org.sbuild.Project

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

class PluginClassLoader(pluginInfo: LoadablePluginInfo, childTrees: Seq[CpTree], parent: ClassLoader)
    extends URLClassLoader(pluginInfo.urls.toArray, parent) {

  import PluginClassLoader._

  private[this] val log = Logger[PluginClassLoader]

  log.debug(s"Init PluginClassLoader (id: ${System.identityHashCode(this)}) for ${pluginInfo.urls}")

  /**
   * The child plugin classloaders controlled by this classloader.
   */
  val pluginClassLoaders: Seq[PluginClassLoader] = childTrees.collect {
    case cpTree if cpTree.pluginInfo.isDefined => new PluginClassLoader(cpTree.pluginInfo.get, cpTree.childs, this)
  }

  /**
   * Load the given class from this classloader.
   * If the loading request was initiated by this classloader itself, no extra restrictions are in place,
   * but if the request was not initially made by this classloader,
   * then only those classes can be loaded, which belong to the set of exported packages,
   */
  def loadPluginClass(className: String): Class[_] = {
    val loadable = if (InnerRequestGuard.isInner(this, className)) true else pluginInfo.checkClassNameInExported(className)

    // First, check if the class has already been loaded
    val loadedClass = findLoadedClass(className) match {
      case loadedClass: Class[_] => loadedClass
      case null =>
        synchronized {
          findLoadedClass(className) match {
            case loadedClass: Class[_] =>
              loadedClass
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
        }
    }

    if (loadable) loadedClass
    else throw new ClassNotFoundException(
      className + "\nAlthough the class is contained in this plugin (\"" + pluginInfo.urls.mkString(",") +
        "\"), it is not exported with " + Constants.ManifestSBuildExportPackage + ".")

  }

  /**
   * Load the given class by delegating to the parent classloader.
   */
  override protected def loadClass(className: String, resolve: Boolean): Class[_] = {
    InnerRequestGuard.within(this, className) {
      // All call from child and this will see all classes.
      parent.loadClass(className)
    }
  }

  override def toString: String = getClass.getSimpleName +
    "(pluginInfo=" + pluginInfo +
    ",parent=" + parent + ")"

  /**
   * register the found plugins to the given project.
   */
  def registerToProject(project: Project): Unit = {
    pluginClassLoaders.foreach(_.registerToProject(project))
    // register found plugin classes
    pluginInfo.pluginClasses.map {
      case LoadablePluginInfo.PluginClasses(instanceClassName, factoryClassName, version) =>
        project.registerPlugin(instanceClassName, factoryClassName, version, this)
    }
  }
}
