package de.tototec.sbuild.ant.tasks

import de.tototec.sbuild.Project
import org.apache.tools.ant.taskdefs.Javac
import de.tototec.sbuild.ant.AntProject
import org.apache.tools.ant.types.{ Path => APath }
import java.io.File

object AntJavac {
  def apply(srcDir: APath = null,
            destDir: File = null,
            fork: java.lang.Boolean = null,
            source: String = null,
            target: String = null,
            encoding: String = null,
            includeAntRuntime: java.lang.Boolean = null,
            debug: java.lang.Boolean = null,
            optimize: java.lang.Boolean = null)(implicit project: Project) =
    new AntJavac(
      srcDir = srcDir,
      destDir = destDir,
      fork = fork,
      source = source,
      target = target,
      encoding = encoding,
      includeAntRuntime = includeAntRuntime,
      debug = debug,
      optimize = optimize
    ).execute
}

class AntJavac()(implicit _project: Project) extends Javac {
  setProject(AntProject())

  def this(srcDir: APath = null,
           destDir: File = null,
           fork: java.lang.Boolean = null,
           source: String = null,
           target: String = null,
           encoding: String = null,
           includeAntRuntime: java.lang.Boolean = null,
           debug: java.lang.Boolean = null,
           optimize: java.lang.Boolean = null)(implicit project: Project) {
    this
    if (srcDir != null) setSrcdir(srcDir)
    if (destDir != null) setDestdir(destDir)
    if (fork != null) setFork(fork.booleanValue)
    if (source != null) setSource(source)
    if (target != null) setTarget(target)
    if (encoding != null) setEncoding(encoding)
    if (includeAntRuntime != null) setIncludeantruntime(includeAntRuntime.booleanValue)
    if (debug != null) setDebug(debug.booleanValue)
    if (optimize != null) setOptimize(optimize)
  }

  def setDestDir(destDir: File) = setDestdir(destDir)
  def setIncludeAntRuntime(includeAntRuntime: Boolean) = setIncludeantruntime(includeAntRuntime)
  def setSrcDir(srcDir: APath) = setSrcdir(srcDir)

}