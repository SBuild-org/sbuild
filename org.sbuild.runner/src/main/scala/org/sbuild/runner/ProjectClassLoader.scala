package org.sbuild.runner

import java.net.URL
import java.net.URLClassLoader
import scala.util.Success
import scala.util.Try
import org.sbuild.Logger
import org.sbuild.Project
import java.util.concurrent.ConcurrentHashMap

/**
 * This classloader first tried to load all classes from the given parent classloader or the classpathUrls.
 * If that fails, it tries to load the classes from internally maintained plugin classloader,
 * but it will only load those classes which are exported by that plugin. [[org.sbuild.Constants.SBuildPluginExportPackage]]
 *
 */
class ProjectClassLoader(classpathUrls: Seq[URL], parent: ClassLoader, classpathTrees: Seq[CpTree])
    extends URLClassLoader(classpathUrls.toArray, parent) {
  //  private[this] val log = Logger[ProjectClassLoader]

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

  val pluginClassLoaders: Seq[PluginClassLoader] = classpathTrees.collect {
    case cpTree if cpTree.pluginInfo.isDefined => new PluginClassLoader(cpTree.pluginInfo.get, cpTree.childs, this)
  }

  override protected def loadClass(className: String, resolve: Boolean): Class[_] = getClassLock(className).synchronized {
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
    //    "(project=" + project +
    //    ",classpathUrls=" + classpathUrls.mkString("[", ",", "]") +
    "(parent=" + parent +
    //    ",pluginInfos=" + classpathTrees.mkString("[", ",", "]") +
    ")"

  private[this] val end = System.currentTimeMillis

  def registerToProject(project: Project): Unit = pluginClassLoaders.foreach(_.registerToProject(project))
}

