package org.sbuild.runner

import java.net.URL
import java.net.URLClassLoader
import scala.util.Success
import scala.util.Try
import org.sbuild.Logger
import org.sbuild.Project
import java.util.concurrent.ConcurrentHashMap

object ProjectClassLoader {

  class DoNotUseThisInstanceException() extends Exception()

  private[this] var parallelRegistered = false

  def apply(name: String, classpathUrls: Seq[URL], parent: ClassLoader, classpathTrees: Seq[CpTree]): ProjectClassLoader = {
    if (!parallelRegistered) {
      try {
        new ProjectClassLoader("do-not-use", Seq(), null, Seq(), true)
      } catch {
        case e: DoNotUseThisInstanceException => // this is ok
      }
      parallelRegistered = true
    }
    new ProjectClassLoader(name, classpathUrls, parent, classpathTrees, false)
  }

}

/**
 * This classloader first tried to load all classes from the given parent classloader or the classpathUrls.
 * If that fails, it tries to load the classes from internally maintained plugin classloader,
 * but it will only load those classes which are exported by that plugin. [[org.sbuild.Constants.SBuildPluginExportPackage]]
 *
 * IMPORTANT: For this class to work correctly, it is important to create one instance and drop it,
 * because it is not well-behaving regarding the classloader specification of Java7+.
 * That's why you can create it only via the companion object.
 */
class ProjectClassLoader private (name: String, classpathUrls: Seq[URL], parent: ClassLoader, classpathTrees: Seq[CpTree],
                                  initParallelClassloading: Boolean)
    extends URLClassLoader(classpathUrls.toArray, parent) {

  if (initParallelClassloading) {
    if (ParallelClassLoader.isJava7) {
      ClassLoader.registerAsParallelCapable()
    }
    throw new ProjectClassLoader.DoNotUseThisInstanceException()
  }

  protected def getClassLock(className: String): AnyRef =
    ParallelClassLoader.withJava7 { () => getClassLoadingLock(className) }.getOrElse { this }

  val pluginClassLoaders: Seq[PluginClassLoader] = classpathTrees.collect {
    case cpTree if cpTree.pluginInfo.isDefined => PluginClassLoader(s"${name}|${cpTree.pluginInfo.map(_.urls)}", cpTree.pluginInfo.get, cpTree.childs, this)
  }

  override protected def loadClass(className: String, resolve: Boolean): Class[_] = getClassLock(className).synchronized {
    try {
      super.loadClass(className, resolve)
    } catch {
      case e: ClassNotFoundException =>
        // println(s"[${Thread.currentThread().getName()}] ProjectClassLoader(${name} ${System.identityHashCode(this)}): About to find class: ${className}")

        // log.trace("Couldn't found class before searching in plugins: " + className)
        // we use an iterator to lazily load the class from next CL until we found it.
        val c = pluginClassLoaders.toIterator.map {
          case pluginClassLoader =>
            Try[Class[_]](pluginClassLoader.loadPluginClass(className))
        }.find(_.isSuccess) match {
          case Some(Success(loadedClass)) =>
            loadedClass
          case _ =>
            // log.trace("Couldn't found class after searching in plugins: " + className + " Classloader: " + this)
            throw new ClassNotFoundException(className)
        }

        // println(s"[${Thread.currentThread().getName()}] ProjectClassLoader(${name} ${System.identityHashCode(this)}): Found class: ${className}")
        c
    }
  }

  override def toString: String = getClass.getSimpleName +
    //    "(project=" + projectScript +
    "(classpathUrls=" + classpathUrls.mkString("[", ",", "]") +
    ",parent=" + parent +
    ",pluginInfos=" + classpathTrees.map(cpTree => cpTree.pluginInfo).mkString("[", ",", "]") +
    ")"

  /**
   * Register the found plugins to the given project.
   */
  def registerToProject(project: Project): Unit = {
    pluginClassLoaders.foreach(_.registerToProject(project))
  }
}

