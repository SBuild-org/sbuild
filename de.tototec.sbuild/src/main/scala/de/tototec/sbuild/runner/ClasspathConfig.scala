package de.tototec.sbuild.runner

import de.tototec.cmdoption.CmdOption

class ClasspathConfig {
  // Classpath of SBuild itself
  @CmdOption(names = Array("--sbuild-cp"), args = Array("CLASSPATH"), hidden = true)
  var sbuildClasspath: String = null

  // Add to the classpath used to load the scala compiler
  @CmdOption(names = Array("--compile-cp"), args = Array("CLASSPATH"), hidden = true)
  var compileClasspath: String = null

  // Add to the classpath used to load the project script
  @CmdOption(names = Array("--project-cp", "--additional-project-cp"), args = Array("CLASSPATH"), hidden = true)
  var projectClasspath: String = null
}
