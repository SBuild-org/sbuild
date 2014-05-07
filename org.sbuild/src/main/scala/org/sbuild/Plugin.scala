package org.sbuild

import scala.reflect.ClassTag

/**
 * An implementation of this trait act as a plugin activator.
 * It is responsible to create new plugin configurations and to apply the plugins functionality to the project,
 * based on the configurations.
 *
 * Implementations are expected to have a single argument constructor with a parameter of type `[[org.sbuild.Project]]`.
 *
 * @tparam T The type of the plugin configuration controlled by this factory.
 */
trait Plugin[T] {

  /**
   * Create a new plugin configuration with the non-empty name `name`.
   * 
   * @param A non-empty name for the plugin configuration.
   */
  def create(name: String): T

  /**
   * Apply the plugin's functionality to the project.
   * To get a handle of the project, implementation should implement a single argument constructor with a parameter of type [[org.sbuild.Project]].
   * @param configurations A sequence of all named plugin configurations.
   *   The pair contains the name and the plugin configuration.
   */
  def applyToProject(configurations: Seq[(String, T)])

}

/**
 * Plugins that will be notified whenever they get (re-)configured.
 */
trait PluginConfigureAware[T] { self: Plugin[T] =>
  /**
   *  A hook called whenever [[Plugin.PluginHandle#configure]] is called.
   */
  def configured(name: String, instance: T)
}

/**
 * This object contains useful `apply` method to activate and access plugin configurations.
 */
object Plugin {

  /**
   * Activate and get the named plugin configuration of type `T`.
   * @tparam T The type of the plugin configuration.
   * @param configName The name of the plugin configuration.
   */
  def apply[T: ClassTag](configName: String)(implicit project: Project): PluginHandle[T] =
    project.getPluginHandle[T](configName)

  /**
   * Handle to a plugin instance.
   */
  trait PluginHandle[T] {
    /**
     * Configure the current plugin.
     * A plugin configuration is typically a case class, and the configurer then calls the `.copy` method to create a new instance with modified properties.
     *
     * @param configurer a function returning the modified configuration.
     */
    def configure(configurer: T => T): PluginHandle[T]

    def postConfigure(configurer: T => T): PluginHandle[T]

    /**
     * Get the current configuration.
     */
    def get: T
    /**
     * Check, whether the configuration was changed after the plugin was activated.
     */
    def isModified: Boolean
    // def disable - to disable an already enabled plugin
  }

  trait PluginInfo {
    def name: String
    def version: String
    def instances: Seq[String]
  }

  def isActive[T: ClassTag](name: String)(implicit project: Project): Boolean = project.isPluginActive[T](name)

  def version[T: ClassTag](implicit project: Project): Option[String] = project.getPluginVersion[T]

}

trait PluginAware {
  def registerPlugin(instanceClassName: String, factoryClassName: String, version: String, classLoader: ClassLoader)
  def finalizePlugins
  def registeredPlugins: Seq[Plugin.PluginInfo]
  def isPluginActive[T: ClassTag](name: String): Boolean
  def isPluginModified[T: ClassTag](name: String): Boolean
  def getPluginVersion[T: ClassTag]: Option[String]
  def getPluginHandle[T: ClassTag](name: String): Plugin.PluginHandle[T]
}
