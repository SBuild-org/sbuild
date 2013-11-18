package de.tototec.sbuild.runner

import java.net.URL
import java.net.URLClassLoader
import scala.util.Success
import scala.util.Try
import de.tototec.sbuild.Logger
import de.tototec.sbuild.Project

/**
 * This classloader first tried to load all classes from the given parent classloader or the classpathUrls.
 * If that fails, it tries to load the classes from internally maintained plugin classloader,
 * but it will only load those classes which are exported by that plugin. [[de.tototec.sbuild.Constants.SBuildPluginExportPackage]]
 *
 */
class ProjectClassLoader(project: Project, classpathUrls: Seq[URL], parent: ClassLoader, classpathTrees: Seq[CpTree])
    extends URLClassLoader(classpathUrls.toArray, parent) {
  //  private[this] val log = Logger[ProjectClassLoader]

  ClassLoader.registerAsParallelCapable()

  val pluginClassLoaders: Seq[PluginClassLoader] = classpathTrees.collect {
    case cpTree if cpTree.pluginInfo.isDefined => new PluginClassLoader(project, cpTree.pluginInfo.get, cpTree.childs, this)
  }

  override protected def loadClass(className: String, resolve: Boolean): Class[_] = getClassLoadingLock(className).synchronized {
    try {
      super.loadClass(className, resolve)
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

  override def toString: String = getClass.getSimpleName +
    "(project=" + project +
    ",classpathUrls=" + classpathUrls.mkString("[", ",", "]") +
    ",parent=" + parent +
    ",pluginInfos=" + classpathTrees.mkString("[", ",", "]") + ")"

  private[this] val end = System.currentTimeMillis
}

