package de.tototec.sbuild

import scala.annotation.Annotation
import scala.reflect.ClassTag

/**
 * WARNING: Do not use this experimental API
 */
trait Plugin[T] {

  //  def instanceType: Class[T]

  def create(name: String): T

  def applyToProject(instances: Seq[(String, T)])

}

/**
 * WARNING: Do not use this experimental API
 */
object Plugin {
  //  def apply[T: ClassTag](name: String = "")(implicit project: Project): T = project.findOrCreatePluginInstance[T](name)
  //  def apply[T: ClassTag](name: String = "")(configurer: T => Unit = { x: T => })(implicit project: Project): Unit = configurer(project.findOrCreatePluginInstance[T](name))

  def apply[T: ClassTag](implicit project: Project): PluginConfigurer[T] = apply[T]("")

  def apply[T: ClassTag](name: String)(implicit project: Project): PluginConfigurer[T] = {
    val instance = project.findOrCreatePluginInstance[T](name)
    new PluginConfigurer[T] {
      override def configure(configurer: T => Unit) { configurer(instance) }
      //      override def get: T = instance
    }
  }

  trait PluginConfigurer[T] {
    def configure(configurer: T => Unit)
    //    def get: T
    // def disable - to disable an already enabled plugin
  }

  trait PluginInfo {
    def name: String
    def version: String
    def instances: Seq[String]
  }
}

trait PluginAware {
  def registerPlugin(instanceClassName: String, factoryClassName: String, version: String, classLoader: ClassLoader)
  //  def registerPlugin(pluginClass: Class[_])
  //  def registerPlugin(plugin: Plugin[_], config: Plugin.Config)
  //  def findOrCreatePluginInstance[I: ClassTag, T <: Plugin[I]: ClassTag]: I
  def findOrCreatePluginInstance[T: ClassTag](name: String): T
  def finalizePlugins
  def registeredPlugins: Seq[Plugin.PluginInfo]
}