package de.tototec.sbuild

import scala.annotation.Annotation
import scala.reflect.ClassTag

/**
 * WARNING: Do not use this experimental API
 */
trait Plugin[T] {

  def instanceType: Class[T]

  //  def defaultName: String

  def create(name: String): T

  def applyToProject(instances: Seq[(String, T)])

}

/**
 * WARNING: Do not use this experimental API
 */
object Plugin {
  //    def apply[I: ClassTag](implicit project: Project): I = project.findOrCreatePluginInstance[I, Plugin[I]]
  def apply[T: ClassTag](name: String)(implicit project: Project): T = project.findOrCreatePluginInstance[T](name)

  //  //  def apply[I: ClassTag, T <: Plugin[I]: ClassTag](implicit project: Project): I = project.findOrCreatePluginInstance[I, T]
  //  def apply[I: ClassTag, T <: Plugin[I]: ClassTag](name: String)(implicit project: Project): I = project.findOrCreatePluginInstance[I, T](name)

  //  case class Config(singleton: Boolean = true)
}

trait PluginAware {
  def registerPlugin(instanceClassName: String, factoryClassName: String, classLoader: ClassLoader)
  //  def registerPlugin(pluginClass: Class[_])
  //  def registerPlugin(plugin: Plugin[_], config: Plugin.Config)
  //  def findOrCreatePluginInstance[I: ClassTag, T <: Plugin[I]: ClassTag]: I
  def findOrCreatePluginInstance[T: ClassTag](name: String): T
  def finalizePlugins
}