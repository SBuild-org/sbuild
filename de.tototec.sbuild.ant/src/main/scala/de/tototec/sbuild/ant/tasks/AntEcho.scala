package de.tototec.sbuild.ant.tasks

import de.tototec.sbuild.Path
import de.tototec.sbuild.Project
import de.tototec.sbuild.ant._
import java.io.File
import org.apache.tools.ant.taskdefs.Echo

object AntEcho {

  def apply(message: String)(implicit proj: Project): Unit = {
    new AntEcho(
      message = message
    ).execute
  }

}

class AntEcho()(implicit _project: Project) extends Echo {
  setProject(AntProject())

  def this(message: String)(implicit proj: Project) {
    this
    setMessage(message)
  }

} 

