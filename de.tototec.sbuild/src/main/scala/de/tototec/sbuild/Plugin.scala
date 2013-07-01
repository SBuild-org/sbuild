package de.tototec.sbuild

/**
 * WARNING: Do not use this experimental API
 */
trait ExperimentalPlugin {

  def init

}

/**
 * WARNING: Do not use this experimental API
 */
object ExperimentalPlugin {

  def apply[T <: ExperimentalPlugin](plugin: T)(implicit project: Project): T = {
    project.registerPlugin(plugin)
    plugin
  }

}