package de.tototec.sbuild.ant.tasks

import de.tototec.sbuild.Path
import de.tototec.sbuild.Project
import de.tototec.sbuild.ant.AntProject
import java.io.File
import org.apache.tools.ant.taskdefs.Delete

object AntDelete {

  def apply()(implicit proj: Project): Delete = {
    val del: Delete = new Delete()
    del.setProject(AntProject()(proj))
    del
  }

  def apply(fileOrDir: String)(implicit proj: Project): Delete = {
    val del: Delete = apply()(proj)
    if(fileOrDir != null) {
      val path: File = Path(fileOrDir)
      if(path.isDirectory) {
        del.setDir(path)
      } else {
        del.setFile(path)
      }
    }
    del
  }

}

