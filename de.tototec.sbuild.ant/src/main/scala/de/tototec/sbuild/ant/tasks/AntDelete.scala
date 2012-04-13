package de.tototec.sbuild.ant.tasks

import de.tototec.sbuild.Path
import de.tototec.sbuild.Project
import de.tototec.sbuild.ant.AntProject
import java.io.File
import org.apache.tools.ant.taskdefs.Delete

object AntDelete {

  def apply(fileOrDir: File)(implicit proj: Project): Unit = {
    new AntDelete(
      fileOrDir = fileOrDir
    ).execute
  }

  def apply(fileOrDir: String)(implicit proj: Project): Unit = {
    new AntDelete(
      fileOrDir = fileOrDir
    ).execute
  }

}

class AntDelete()(implicit _project: Project) extends Delete {
  setProject(AntProject())

  def this(fileOrDir: String)(implicit proj: Project) {
    this
    if(fileOrDir != null) {
      val path: File = Path(fileOrDir)
      if(path.isDirectory) {
        setDir(path)
      } else {
        setFile(path)
      }
    }
  }

  def this(fileOrDir: File)(implicit proj: Project) = this(fileOrDir.getPath)

}

