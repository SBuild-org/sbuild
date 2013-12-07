package de.tototec.sbuild

import scala.annotation.Annotation
import scala.reflect.ClassTag

/**
 * An implementation of this trait act as a plugin activator.
 * It is responsible to create new plugin instances and to apply the plugins functionality to the project,
 * based on the plugin instances.
 *
 * Implementations are expected to have a single arg constructor with a parameter of type `[[de.tototec.sbuild.Project]]`.
 *
 * @tparam T The type of the plugin instance controlled by this factory.
 */
trait Plugin[T] {

  /**
   * Create a new plugin instance with the name `name`.
   * Keep in mind that it is allowed that name in the empty string (`""`),
   * which has the meaning "an instance with the default configuration".
   */
  def create(name: String): T

  /**
   * Apply the plugin's functionality to the project.
   * To get a handle of the project, implementation should implement a single arg constructor with a parameter of type [[de.tototec.sbuild.Project]].
   * @param instances A sequence of all named plugin instances.
   *   The pair contains the name and the instance.
   */
  def applyToProject(instances: Seq[(String, T)])

}

/**
 *
 */
object Plugin {

  /**
   * Activate an get a default named instance of a plugin of type `T`.
   * @tparam T The type of the plugin instance.
   */
  def apply[T: ClassTag](implicit project: Project): PluginConfigurer[T] = apply[T]("")

  /**
   * Activate an get a named instance of a plugin ot type `T`.
   * @tparam T The type of the plugin instance.
   * @param name The name of this plugin instance.
   */
  def apply[T: ClassTag](name: String)(implicit project: Project): PluginConfigurer[T] = {
    val instance = project.findOrCreatePluginInstance[T](name)
    new PluginConfigurer[T] {
      override def configure(configurer: T => Unit) { configurer(instance) }
      //      override def get: T = instance
    }
  }

  /**
   * Handle to a plugin instance.
   */
  trait PluginConfigurer[T] {
    def configure(configurer: T => Unit)
    // def get: T
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