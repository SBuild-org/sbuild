package de.tototec.sbuild.addons.scala

import de.tototec.sbuild.Project
import java.io.File
import de.tototec.sbuild.ProjectConfigurationException
import de.tototec.sbuild.addons.support.ForkSupport

// Since SBuild 0.3.1.9000
object ScalaRepl {
  def apply(
    replClasspath: Seq[File] = null,
    classpath: Seq[File] = null,
    addReplClasspathToClasspath: Boolean = true)(implicit _project: Project) =
    new ScalaRepl(
      replClasspath = replClasspath,
      classpath = classpath,
      addReplClasspathToClasspath = addReplClasspathToClasspath
    ).execute
}

// Since SBuild 0.3.1.9000
class ScalaRepl(
  var replClasspath: Seq[File] = null,
  var classpath: Seq[File] = null,
  var addReplClasspathToClasspath: Boolean = true)(implicit _project: Project) {

  def execute() {
    if (classpath == null || classpath.isEmpty) {
      throw new ProjectConfigurationException("You must specify an classpath, including the scala library and the scala compiler classes.")
    }

    val (javaCp, loadCp) = replClasspath match {
      case null => (classpath, classpath)
      case x if x.isEmpty => (classpath, classpath)
      case x =>
        val loadCp =
          if (addReplClasspathToClasspath) x ++ classpath
          else classpath
        (x, loadCp)
    }

    val replClassName = "scala.tools.nsc.MainGenericRunner"
    var args: Array[String] = Array(replClassName, "-cp", ForkSupport.pathAsArg(loadCp))

    ForkSupport.runJavaAndWait(classpath = javaCp, arguments = args, interactive = true, failOnError = true)

  }

}