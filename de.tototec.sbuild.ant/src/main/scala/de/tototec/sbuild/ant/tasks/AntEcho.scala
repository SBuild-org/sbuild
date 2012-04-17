package de.tototec.sbuild.ant.tasks

import de.tototec.sbuild.Path
import de.tototec.sbuild.Project
import de.tototec.sbuild.ant._
import java.io.File
import org.apache.tools.ant.taskdefs.Echo
import org.apache.tools.ant.taskdefs.Echo.EchoLevel

object AntEcho {

  def apply(message: String = null,
            level: EchoLevel = null,
            file: File = null,
            append: java.lang.Boolean = null,
            encoding: String = null,
            force: java.lang.Boolean = null)(implicit proj: Project): Unit = {
    new AntEcho(
      message = message,
      level = level,
      file = file,
      append = append,
      encoding = encoding,
      force = force
    ).execute
  }

}

class AntEcho()(implicit _project: Project) extends Echo {
  setProject(AntProject())

  def this(message: String = null,
           level: EchoLevel = null,
           file: File = null,
           append: java.lang.Boolean = null,
           encoding: String = null,
           force: java.lang.Boolean = null)(implicit proj: Project) {
    this
    if (message != null) setMessage(message)
    if (level != null) setLevel(level)
    if (file != null) setFile(file)
    if (append != null) setAppend(append.booleanValue)
    if (encoding != null) setEncoding(encoding)
    if (force != null) setForce(force.booleanValue)
  }

  override def setFile(file: File) = super.setFile(Path(file.getPath))

} 

 