package org.sbuild

/**
 * Plugins that depend on other plugins need to implement this trait.
 * In `[[PluginWithDependencies#dependsOn]]`, they need to return the dependencies.
 * SBuild will ensure, that plugins will be applied before their dependencies are applied to the project.
 */
trait PluginWithDependencies { self: Plugin[_] =>
  /**
   * The classes of the dependencies of this plugin.
   */
  def dependsOn: Seq[PluginDependency]
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
