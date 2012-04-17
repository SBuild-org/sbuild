package de.tototec.sbuild.runner

import de.tototec.cmdoption.CmdOption

class Config {
  @CmdOption(names = Array("--help", "-h"), isHelp = true, description = "Show this help screen.")
  var help = false

  @CmdOption(names = Array("--version"), description = "Show SBuild version.")
  var showVersion = false

  @CmdOption(names = Array("--buildfile", "-f"), args = Array("FILE"),
    description = "The buildfile to use (default: SBuild.scala).")
  var buildfile = "SBuild.scala"

  @CmdOption(names = Array("--verbose", "-v"), description = "Be verbose when running.")
  var verbose = false

  @CmdOption(names = Array("--list-targets", "-l"),
    description = "Show a list of targets defined in the current buildfile")
  val listTargets = false

  @CmdOption(names = Array("--define", "-D"), args = Array("KEY=VALUE"), maxCount = -1,
    description = "Define or override properties. If VALUE is omitted it defaults to \"true\".")
  def addDefine(keyValue: String) {
    keyValue.split("=", 2) match {
      case Array(key, value) => defines.put(key, value)
      case Array(key) => defines.put(key, "true")
    }
  }
  val defines: java.util.Map[String, String] = new java.util.LinkedHashMap()

  @CmdOption(names = Array("--clean"),
    description = "Remove all generated output and caches before start. This will force a new compile of the buildfile.")
  var clean: Boolean = false

  @CmdOption(args = Array("TARGETS"), maxCount = -1, description = "The target(s) to execute (in order).")
  val params = new java.util.LinkedList[String]()
}