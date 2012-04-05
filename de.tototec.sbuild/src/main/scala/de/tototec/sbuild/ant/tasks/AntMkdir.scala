package de.tototec.sbuild.ant.tasks

import de.tototec.sbuild.Path
import de.tototec.sbuild.Project
import de.tototec.sbuild.ant._
import java.io.File
import org.apache.tools.ant.taskdefs.Mkdir

object AntMkdir {

  def apply(dir: String)(implicit proj: Project): Mkdir = {
    apply(Path(dir)(proj))
  }

  def apply(dir: File)(implicit proj: Project): Mkdir = {
    val mkdir: Mkdir = new Mkdir()
    mkdir.setProject(AntProject()(proj))
    mkdir.setDir(Path(dir.getPath())(proj))
    mkdir
  }

}

