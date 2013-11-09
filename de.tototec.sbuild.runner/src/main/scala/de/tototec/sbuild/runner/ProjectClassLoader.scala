package de.tototec.sbuild.runner

import java.net.URLClassLoader
import java.net.URL
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import java.util.jar.JarInputStream
import de.tototec.sbuild.Constants
import de.tototec.sbuild.Logger

/**
 * This classloader first tried to load all classes from the given parent classloader or the classpathUrls.
 * If that fails, it tries to load the classes from internally maintained plugin classloader,
 * but it will only load those classes which are exported by that plugin. [[de.tototec.sbuild.Constants.SBuildPluginExportPackage]]
 *
 */
class ProjectClassLoader(classpathUrls: Array[URL], pluginUrls: Array[URL], parent: ClassLoader)
    extends URLClassLoader(classpathUrls, parent) {

  ClassLoader.registerAsParallelCapable()

  val pluginClassLoaders: Seq[PluginExportClassLoader] = pluginUrls.toSeq.map(url => new PluginExportClassLoader(url, this))

  override protected def loadClass(className: String, resolve: Boolean): Class[_] = getClassLoadingLock(className).synchronized {
    try {
      super.loadClass(className, resolve)
    } catch {
      case e: ClassNotFoundException =>
        LookupCycleGuard.get match {
          case null => try {
            LookupCycleGuard.set(className)
            pluginClassLoaders.toIterator.map { pluginClassLoader =>
              Try[Class[_]](pluginClassLoader.loadPluginClass(className))
            }.find(_.isSuccess) match {
              case Some(Success(loadedClass)) => loadedClass
              case _ => throw new ClassNotFoundException(className)
            }
          } finally {
            LookupCycleGuard.set(null)
          }
          case cl => throw e
        }
    }
  }

}

private object LookupCycleGuard extends ThreadLocal[String] 