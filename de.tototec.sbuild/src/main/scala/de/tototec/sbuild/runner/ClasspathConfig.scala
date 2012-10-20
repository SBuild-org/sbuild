package de.tototec.sbuild.runner

import de.tototec.cmdoption.CmdOption
import java.io.File
import java.util.Properties
import java.io.FileReader
import java.io.BufferedInputStream
import java.io.FileInputStream

object ClasspathConfig {

  def readFromPropertiesFile(propertiesFile: File): ClasspathConfig = {
    val libDir = propertiesFile.getAbsoluteFile.getCanonicalFile.getParentFile

    val stream = new BufferedInputStream(new FileInputStream(propertiesFile))
    val props = new Properties()
    props.load(stream)

    readFromProperties(libDir, props)
  }

  def readFromProperties(sbuildLibDir: File, properties: Properties): ClasspathConfig = {

    def splitAndPrepend(propertyValue: String): Array[String] = propertyValue.split(";|:").map {
      case lib if new File(lib).isAbsolute() => lib
      case lib => new File(sbuildLibDir.getAbsoluteFile, lib).getPath
    }

    val config = new ClasspathConfig
    config.sbuildClasspath = splitAndPrepend(properties.getProperty("sbuildClasspath"))
    config.compileClasspath = splitAndPrepend(properties.getProperty("compileClasspath"))
    config.projectClasspath = splitAndPrepend(properties.getProperty("projectClasspath"))

    config
  }
}

class ClasspathConfig {
  // Classpath of SBuild itself
  @CmdOption(names = Array("--sbuild-cp"), args = Array("CLASSPATH"), hidden = true)
  def sbuildClasspath_=(classpath: String): Unit = sbuildClasspath = classpath match {
    case null => Array[String]()
    case x => x.split(";|:")
  }
  var sbuildClasspath: Array[String] = Array()

  // Add to the classpath used to load the scala compiler
  @CmdOption(names = Array("--compile-cp"), args = Array("CLASSPATH"), hidden = true)
  def compileClasspath_=(classpath: String): Unit = compileClasspath = classpath match {
    case null => Array[String]()
    case x => x.split(";|:")
  }
  var compileClasspath: Array[String] = Array()

  // Add to the classpath used to load the project script
  @CmdOption(names = Array("--project-cp", "--additional-project-cp"), args = Array("CLASSPATH"), hidden = true)
  def projectClasspath_=(classpath: String): Unit = projectClasspath = classpath match {
    case null => Array[String]()
    case x => x.split(";|:")
  }
  var projectClasspath: Array[String] = Array()

  @CmdOption(names = Array("--no-fsc"), description = "Do not try to use the fast scala compiler (client/server)")
  var noFsc: Boolean = false

  def validate: Boolean = {
    sbuildClasspath.forall { new File(_).exists } &&
      compileClasspath.forall { new File(_).exists } &&
      projectClasspath.forall { new File(_).exists }
  }

}