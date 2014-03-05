package de.tototec.sbuild

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

sealed trait PluginDependency {
  def pluginClass: Class[_]
}

object PluginDependency {

  /**
   * A dependency to another plugin (instance) class.
   */
  case class Basic(override val pluginClass: Class[_]) extends PluginDependency

  /**
   * A Dependency with version constraints. The version can be a minimal version or a range.
   */
  case class Versioned(
    override val pluginClass: Class[_],
    val version: String)
      extends PluginDependency

  def apply(pluginClass: Class[_]): PluginDependency = Basic(pluginClass)
  def apply(pluginClass: Class[_], version: String): PluginDependency = Versioned(pluginClass, version)

  /**
   * Implicit conversion for convenience and backward source compatibility.
   */
  implicit def pluginDependency(pluginClass: Class[_]): PluginDependency = apply(pluginClass)
}

trait PluginWithDependencies { self: Plugin[_] =>
  def dependsOn: Seq[PluginDependency]
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
  def apply[T: ClassTag](name: String)(implicit project: Project): PluginHandle[T] =
    project.getPluginHandle[T](name)

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

    def postConfigure(configurer: T => T): PluginHandle[T]

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

  def isActive[T: ClassTag](implicit project: Project): Boolean = isActive[T]("")

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