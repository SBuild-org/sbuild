package de.tototec.sbuild.ant.tasks.scala_tools_ant

import de.tototec.sbuild.ant.AntProject
import de.tototec.sbuild.Project
import java.io.File
import org.apache.tools.ant.types.{ Path => APath }
import scala.tools.ant.Scalac

object AntScalac {
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
    new AntScalac(
      srcDir = srcDir,
      destDir = destDir,
      fork = fork,
      target = target,
      encoding = encoding,
      deprecation = deprecation,
      unchecked = unchecked,
      classpath = classpath,
      force = force,
      logging = logging
    ).execute
}

class AntScalac()(implicit _project: Project) extends Scalac {
  setProject(AntProject())

  def this(srcDir: APath = null,
           destDir: File = null,
           fork: java.lang.Boolean = null,
           target: String = null,
           encoding: String = null,
           deprecation: String = null,
           unchecked: String = null,
           classpath: APath = null,
           force: java.lang.Boolean = null,
           logging: String = null)(implicit _project: Project) {
    this
    if (srcDir != null) setSrcdir(srcDir)
    if (destDir != null) setDestdir(destDir)
    if (fork != null) setFork(fork.booleanValue)
    if (target != null) setTarget(target)
    if (encoding != null) setEncoding(encoding)
    if (deprecation != null) setDeprecation(deprecation)
    if (unchecked != null) setUnchecked(unchecked)
    if (classpath != null) setClasspath(classpath)
    if (force != null) setForce(force.booleanValue)
    if (logging != null) setLogging(logging)
  }

  def setDestDir(destDir: File) = setDestdir(destDir)
  def setSrcDir(srcDir: APath) = setSrcdir(srcDir)

}