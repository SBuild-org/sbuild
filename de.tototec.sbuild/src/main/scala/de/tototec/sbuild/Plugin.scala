package de.tototec.sbuild

trait Plugin {

  def init

}

object Plugin {

  def apply[T <: Plugin](plugin: T)(implicit project: Project): T = {
    project.registerPlugin(plugin)
    plugin
  }

}