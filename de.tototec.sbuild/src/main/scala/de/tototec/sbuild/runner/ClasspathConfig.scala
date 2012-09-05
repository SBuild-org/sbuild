package de.tototec.sbuild.runner

import de.tototec.cmdoption.CmdOption
import java.io.File

class ClasspathConfig {
  // Classpath of SBuild itself
  @CmdOption(names = Array("--sbuild-cp"), args = Array("CLASSPATH"), hidden = true)
  var _sbuildClasspath: String = null
  def sbuildClasspath: Array[String] = _sbuildClasspath match {
    case null => Array()
    case x => x.split(File.pathSeparator)
  }

  // Add to the classpath used to load the scala compiler
  @CmdOption(names = Array("--compile-cp"), args = Array("CLASSPATH"), hidden = true)
  var _compileClasspath: String = null
  def compileClasspath: Array[String] = _compileClasspath match {
    case null => Array()
    case x => x.split(File.pathSeparator)
  }

  // Add to the classpath used to load the project script
  @CmdOption(names = Array("--project-cp", "--additional-project-cp"), args = Array("CLASSPATH"), hidden = true)
  var _projectClasspath: String = null
  def projectClasspath: Array[String] = _projectClasspath match {
    case null => Array()
    case x => x.split(File.pathSeparator)
  }

  @CmdOption(names = Array("--no-fsc"), description = "Do not try to use the fast scala compiler (client/server)")
  var noFsc: Boolean = false

  def validate: Boolean = {
    sbuildClasspath.forall { new File(_).exists } &&
      compileClasspath.forall { new File(_).exists } &&
      projectClasspath.forall { new File(_).exists }
  }
}
