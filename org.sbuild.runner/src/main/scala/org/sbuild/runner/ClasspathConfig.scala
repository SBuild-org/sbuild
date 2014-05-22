package org.sbuild.runner

import de.tototec.cmdoption.CmdOption
import java.io.File
import java.util.Properties
import java.io.FileReader
import java.io.BufferedInputStream
import java.io.FileInputStream
import org.sbuild.ProjectConfigurationException
import org.sbuild.Path

case class Classpaths(
    sbuildLibDir: Option[File] = None,
    sbuildClasspath: Array[File] = Array(),
    compileClasspath: Array[File] = Array(),
    projectCompileClasspath: Array[File] = Array(),
    projectRuntimeClasspath: Array[File] = Array(),
    compilerPluginJars: Array[File] = Array(),
    projectBootstrapJars: Array[File] = Array(),
    projectBootstrapClasspath: Array[File] = Array(),
    projectBootstrapClass: String = "org.sbuild.runner.bootstrap.SBuildBootstrap") {

  def validate(): Boolean =
    (sbuildClasspath ++ compileClasspath ++ projectCompileClasspath ++ projectRuntimeClasspath ++ compilerPluginJars).
      forall { _.exists }

}

class ClasspathConfig {

  private[this] var _classpaths: Classpaths = Classpaths()

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

  def classpaths: Classpaths = _classpaths

  // Classpath of SBuild itself
  def sbuildClasspath: Array[File] = classpaths.sbuildClasspath

  // Add to the classpath used to load the scala compiler
  def compileClasspath: Array[File] = classpaths.compileClasspath

  // Add to the classpath used to load the project script
  def projectCompileClasspath: Array[File] = classpaths.projectCompileClasspath

  // Add to the classpath used to load the project script
  def projectRuntimeClasspath: Array[File] = classpaths.projectRuntimeClasspath

  def compilerPluginJars: Array[File] = classpaths.compilerPluginJars

  @CmdOption(names = Array("--no-fsc"), description = "Do not try to use the fast scala compiler (client/server)")
  var noFsc: Boolean = true

  @CmdOption(names = Array("--fsc"), description = "Use the fast scala compiler (client/server). The fsc compiler of the correct Scala version must be installed.", conflictsWith = Array("--no-fsc"))
  def fsc = noFsc = false

  def validate: Boolean = classpaths.validate()

  def readFromPropertiesFile(propertiesFile: File) {
    val libDir = Path.normalize(propertiesFile).getParentFile

    val stream = new BufferedInputStream(new FileInputStream(propertiesFile))
    val props = new Properties()
    props.load(stream)

    readFromProperties(libDir, props)
  }

  def readFromProperties(sbuildLibDir: File, properties: Properties) {
    val absLibDir = sbuildLibDir.getAbsoluteFile

    // all files in the classpath config are relative to the lib dir
    
    def splitAndPrepend(propertyValue: String): Array[File] = propertyValue match {
      case null | "" => Array()
      case v => v.split(";|:").map { lib =>
        new File(absLibDir, lib)
      }
    }

    _classpaths = Classpaths(
      sbuildLibDir = Some(absLibDir),
      sbuildClasspath = splitAndPrepend(properties.getProperty("sbuildClasspath")),
      compileClasspath = splitAndPrepend(properties.getProperty("compileClasspath")),
      projectCompileClasspath = splitAndPrepend(properties.getProperty("projectCompileClasspath")),
      projectRuntimeClasspath = splitAndPrepend(properties.getProperty("projectRuntimeClasspath")),
      compilerPluginJars = splitAndPrepend(properties.getProperty("compilerPluginJar")),
      projectBootstrapJars = splitAndPrepend(properties.getProperty("projectBootstrapJars")),
      projectBootstrapClasspath = splitAndPrepend(properties.getProperty("projectBootstrapClasspath"))
    )
  }

}
