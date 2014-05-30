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
    case _ => throw new IllegalArgumentException("SBuild HOME directory must be an absolute path.")
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
  @CmdOption(names = Array("--project-compile-cp", "--additional-project-compile-cp"), args = Array("CLASSPATH"), hidden = true)
  def projectCompileClasspath_=(classpath: String): Unit = projectCompileClasspath = classpath match {
    case null => Array[String]()
    case x => x.split(";|:")
  }
  var projectCompileClasspath: Array[String] = Array()

    // Add to the classpath used to load the project script
  @CmdOption(names = Array("--project-runtime-cp", "--additional-project-runtime-cp"), args = Array("CLASSPATH"), hidden = true)
  def projectRuntmeClasspath_=(classpath: String): Unit = projectRuntimeClasspath = classpath match {
    case null => Array[String]()
    case x => x.split(";|:")
  }
  var projectRuntimeClasspath: Array[String] = Array()

  @CmdOption(names = Array("--compiler-plugin-jar"), args = Array("JAR"), hidden = true)
  def compilerPluginJars_=(jars: String): Unit = compilerPluginJars = jars match {
    case null => Array[String]()
    case x => x.split(";|:")
  }
  var compilerPluginJars: Array[String] = Array()

  @CmdOption(names = Array("--no-fsc"), description = "Do not try to use the fast scala compiler (client/server)")
  var noFsc: Boolean = true

  @CmdOption(names = Array("--fsc"), description = "Use the fast scala compiler (client/server). The fsc compiler of the correct Scala version must be installed.", conflictsWith = Array("--no-fsc"))
  def fsc = noFsc = false

  def validate: Boolean = {
    sbuildClasspath.forall { new File(_).exists } &&
      compileClasspath.forall { new File(_).exists } &&
      projectCompileClasspath.forall { new File(_).exists } &&
      projectRuntimeClasspath.forall { new File(_).exists } &&
      compilerPluginJars.forall { new File(_).exists }
  }

  def readFromPropertiesFile(propertiesFile: File) {
    val libDir = Path.normalize(propertiesFile).getParentFile

    val stream = new BufferedInputStream(new FileInputStream(propertiesFile))
    val props = new Properties()
    props.load(stream)

    readFromProperties(libDir, props)
  }

  def readFromProperties(sbuildLibDir: File, properties: Properties) {

    def splitAndPrepend(propertyValue: String): Array[String] = propertyValue match {
      case null | "" => Array()
      case v => v.split(";|:").map {
        case lib if new File(lib).isAbsolute() => lib
        case lib => new File(sbuildLibDir.getAbsoluteFile, lib).getPath
      }
    }

    sbuildClasspath = splitAndPrepend(properties.getProperty("sbuildClasspath"))
    compileClasspath = splitAndPrepend(properties.getProperty("compileClasspath"))
    projectCompileClasspath = splitAndPrepend(properties.getProperty("projectCompileClasspath"))
    projectRuntimeClasspath = splitAndPrepend(properties.getProperty("projectRuntimeClasspath"))
    compilerPluginJars = splitAndPrepend(properties.getProperty("compilerPluginJar"))
  }

}
