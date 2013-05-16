package de.tototec.sbuild.runner

import de.tototec.cmdoption.CmdOption
import java.io.File
import java.util.Properties
import java.io.FileReader
import java.io.BufferedInputStream
import java.io.FileInputStream
import de.tototec.sbuild.ProjectConfigurationException
import de.tototec.sbuild.Path

class ClasspathConfig {
  @CmdOption(names = Array("--sbuild-home"), args = Array("PATH"), hidden = true)
  def sbuildHomeDir_=(sbuildHomeDirString: String): Unit = sbuildHomeDir = new File(sbuildHomeDirString)
  def sbuildHomeDir_=(sbuildHomeDir: File): Unit = _sbuildHomeDir = sbuildHomeDir match {
    case f if f.isAbsolute =>
      readFromPropertiesFile(new File(f, "lib/classpath.properties"))
      f
    case _ => throw new IllegalArgumentException("SBuild HOME directory must be an abssolute path.")
  }
  def sbuildHomeDir = _sbuildHomeDir
  var _sbuildHomeDir: File = _

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
  var noFsc: Boolean = true
  
  @CmdOption(names = Array("--fsc"), description = "Use the fast scala compiler (client/server). The fsc compiler of the correct Scala version must be installed.", conflictsWith = Array("--no-fsc"))
  def fsc = noFsc = false

  def validate: Boolean = {
    sbuildClasspath.forall { new File(_).exists } &&
      compileClasspath.forall { new File(_).exists } &&
      projectClasspath.forall { new File(_).exists }
  }

  def readFromPropertiesFile(propertiesFile: File) {
    val libDir = Path.normalize(propertiesFile).getParentFile

    val stream = new BufferedInputStream(new FileInputStream(propertiesFile))
    val props = new Properties()
    props.load(stream)

    readFromProperties(libDir, props)
  }

  def readFromProperties(sbuildLibDir: File, properties: Properties) {

    def splitAndPrepend(propertyValue: String): Array[String] = propertyValue.split(";|:").map {
      case lib if new File(lib).isAbsolute() => lib
      case lib => new File(sbuildLibDir.getAbsoluteFile, lib).getPath
    }

    sbuildClasspath = splitAndPrepend(properties.getProperty("sbuildClasspath"))
    compileClasspath = splitAndPrepend(properties.getProperty("compileClasspath"))
    projectClasspath = splitAndPrepend(properties.getProperty("projectClasspath"))
  }

}
