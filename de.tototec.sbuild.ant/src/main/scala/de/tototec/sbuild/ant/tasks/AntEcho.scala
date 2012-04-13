package de.tototec.sbuild.ant.tasks

import de.tototec.sbuild.Path
import de.tototec.sbuild.Project
import de.tototec.sbuild.ant._
import java.io.File
import org.apache.tools.ant.taskdefs.Echo

object AntEcho {

  def apply(message: String)(implicit proj: Project): Echo = {
    val echo: Echo = new Echo()
    echo.setProject(AntProject()(proj))
    echo.setMessage(message)
    echo
  }

}

