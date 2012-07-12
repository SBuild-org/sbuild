package de.tototec.sbuild.ant.tasks.aQute_bnd_ant

import aQute.bnd.ant.BndTask
import de.tototec.sbuild.Project
import de.tototec.sbuild.ant.AntProject
import java.io.File
import org.apache.tools.ant.types.Path
import scala.collection.JavaConversions._

object AntBnd {
  def apply(sourcePath: String = null,
            files: String = null,
            output: File = null,
            exceptions: java.lang.Boolean = null,
            failOk: java.lang.Boolean = null,
            eclipse: java.lang.Boolean = null,
            classpath: String = null)(implicit _project: Project) =
    new AntBnd(
      sourcePath = sourcePath,
      files = files,
      output = output,
      exceptions = exceptions,
      failOk = failOk,
      eclipse = eclipse,
      classpath = classpath
    ).execute
}

class AntBnd()(implicit _project: Project) extends BndTask {
  setProject(AntProject())

  def this(sourcePath: String = null,
           files: String = null,
           output: File = null,
           exceptions: java.lang.Boolean = null,
           failOk: java.lang.Boolean = null,
           eclipse: java.lang.Boolean = null,
           classpath: String = null)(implicit _project: Project) {
    this
    if (sourcePath != null) setSourcepath(sourcePath)
    if (files != null) setFiles(files)
    if (output != null) setOutput(output)
    if (exceptions != null) setExceptions(exceptions)
    if (failOk != null) setFailok(failOk)
    if (eclipse != null) setEclipse(eclipse)
    if (classpath != null) setClasspath(classpath)
  }

  def setSourcePath(sourcePath: String) = setSourcepath(sourcePath)
  def setFailOk(failOk: Boolean) = setFailok(failOk)
  
  def setClasspath(classpath: Path) = classpath.iterator.mkString(",")

}