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
 * This object contains useful `apply` method to activate and access plugin instances.
 */
object Plugin {

  /**
   * Activate and get a default named instance of a plugin of type `T`.
   * @tparam T The type of the plugin instance.
   */
  def apply[T: ClassTag](implicit project: Project): PluginHandle[T] = apply[T]("")

  /**
   * Activate and get a named instance of a plugin ot type `T`.
   * @tparam T The type of the plugin instance.
   * @param name The name of this plugin instance.
   */
  def apply[T: ClassTag](name: String)(implicit project: Project): PluginHandle[T] = {
    // trigger plugin activation
    project.findOrCreatePluginInstance[T](name)

    new PluginHandle[T] {
      override def configure(configurer: T => T): PluginHandle[T] = {
        project.findAndUpdatePluginInstance[T](name, configurer)
        this
      }
      override def get: T = project.findOrCreatePluginInstance[T](name)
      override def isModified: Boolean = project.isPluginInstanceModified[T](name)
    }
  }

  /**
   * Handle to a plugin instance.
   */
  trait PluginHandle[T] {
    /**
     * Configure the current plugin.
     * A plugin instance is typically a case class, and the configurer then calls the `.copy` method to create a new instance with modified properties.
     *
     * @param configurer a function returning the modified configuration.
     */
    def configure(configurer: T => T): PluginHandle[T]
    /**
     * Get the current configuration.
     */
    def get: T
    /**
     * Check, whether to configuration was changed after the plugin was activated.
     */
    def isModified: Boolean
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
  def findOrCreatePluginInstance[T: ClassTag](name: String): T
  def findAndUpdatePluginInstance[T: ClassTag](name: String, updater: T => T): Unit
  def finalizePlugins
  def registeredPlugins: Seq[Plugin.PluginInfo]
  def isPluginInstanceModified[T: ClassTag](name: String): Boolean
}