package de.tototec.sbuild

import scala.annotation.Annotation
import scala.reflect.ClassTag

//class plugins(value: String*) extends Annotation

/**
 * WARNING: Do not use this experimental API
 */
trait Plugin[T] {

  def instanceType: Class[T]

  def defaultName: String

  def create(name: String): T

  def applyToProject(name: String, pluginContext: T)

}

/**
 * WARNING: Do not use this experimental API
 */
object Plugin {
  def apply[I: ClassTag](implicit project: Project): I = project.findOrCreatePluginInstance[I, Plugin[I]]
  def apply[I: ClassTag](name: String)(implicit project: Project): I = project.findOrCreatePluginInstance[I, Plugin[I]](name)

  def apply[I: ClassTag, T <: Plugin[I]: ClassTag](implicit project: Project): I = project.findOrCreatePluginInstance[I, T]
  def apply[I: ClassTag, T <: Plugin[I]: ClassTag](name: String)(implicit project: Project): I = project.findOrCreatePluginInstance[I, T](name)

  case class Config(singleton: Boolean = true)
}

trait PluginAware {
  def registerPlugin(pluginClass: Class[_], config: Plugin.Config)
  def registerPlugin(plugin: Plugin[_], config: Plugin.Config)
  def findOrCreatePluginInstance[I: ClassTag, T <: Plugin[I]: ClassTag]: I
  def findOrCreatePluginInstance[I: ClassTag, T <: Plugin[I]: ClassTag](name: String): I
  def finalizePlugins
}