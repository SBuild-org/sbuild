package de.tototec.sbuild.ant.tasks.scala_tools_ant

import de.tototec.sbuild.Project
import scala.tools.ant.Scaladoc
import de.tototec.sbuild.ant.AntProject
import org.apache.tools.ant.types.{ Path => APath }
import java.io.File

object AntScaladoc {
  def apply(srcDir: APath = null,
            destDir: File = null,
            fork: java.lang.Boolean = null,
            target: String = null,
            encoding: String = null,
            deprecation: String = null,
            unchecked: String = null,
            classpath: APath = null,
            force: java.lang.Boolean = null,
            logging: String = null)(implicit _project: Project) =
    new AntScaladoc(
      srcDir = srcDir,
      destDir = destDir,
      encoding = encoding,
      deprecation = deprecation,
      unchecked = unchecked,
      classpath = classpath
    ).execute
}

class AntScaladoc()(implicit _project: Project) extends Scaladoc {
  setProject(AntProject())

  def this(srcDir: APath = null,
           destDir: File = null,
           encoding: String = null,
           deprecation: String = null,
           unchecked: String = null,
           classpath: APath = null)(implicit _project: Project) {
    this
    if (srcDir != null) setSrcdir(srcDir)
    if (destDir != null) setDestdir(destDir)
    if (encoding != null) setEncoding(encoding)
    if (deprecation != null) setDeprecation(deprecation)
    if (unchecked != null) setUnchecked(unchecked)
    if (classpath != null) setClasspath(classpath)
  }

  def setDestDir(destDir: File) = setDestdir(destDir)
  def setSrcDir(srcDir: APath) = setSrcdir(srcDir)
  
}