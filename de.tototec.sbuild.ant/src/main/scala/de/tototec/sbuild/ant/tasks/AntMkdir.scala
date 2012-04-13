package de.tototec.sbuild.ant.tasks

import de.tototec.sbuild.Path
import de.tototec.sbuild.Project
import de.tototec.sbuild.ant._
import java.io.File
import org.apache.tools.ant.taskdefs.Mkdir

object AntMkdir {

  def apply(dir: File)(implicit proj: Project): Unit = new AntMkdir(
    dir = dir
  ).execute

  def apply(dir: String)(implicit proj: Project): Unit = new AntMkdir(
    dir = dir
  ).execute

}

class AntMkdir()(implicit _project: Project) extends Mkdir {
  setProject(AntProject())

  def this(dir: String)(implicit proj: Project) {
    this
    setDir(Path(dir))
  }

  def this(dir: File)(implicit proj: Project) = this(dir.getPath)

}