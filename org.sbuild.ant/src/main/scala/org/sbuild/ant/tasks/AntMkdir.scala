package org.sbuild.ant.tasks

import org.sbuild.Path
import org.sbuild.Project
import org.sbuild.ant._
import java.io.File
import org.apache.tools.ant.taskdefs.Mkdir

object AntMkdir {

  def apply(dir: File)(implicit proj: Project): Unit = new AntMkdir(dir = dir).execute

}

class AntMkdir()(implicit _project: Project) extends Mkdir {
  setProject(AntProject())

  def this(dir: File)(implicit proj: Project) {
    this
    setDir(dir)
  }

}