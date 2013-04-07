package de.tototec.sbuild

trait Plugin {

  def init

}

object Plugin {

  def apply[T <: Plugin](plugin: T)(implicit project: Project): T = {
    project.log.log(LogLevel.Debug, s"""About to initialize plugin "${plugin.getClass()}": ${plugin.toString()}""")
    plugin.init
    project.log.log(LogLevel.Debug, s"""Initialized plugin "${plugin.getClass()}": ${plugin.toString()}""")
    plugin
  }

}