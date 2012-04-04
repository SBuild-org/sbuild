package de.tototec.sbuild.ant

import de.tototec.sbuild.Project
import java.io.File
import de.tototec.sbuild.Path

object FileSet {
  type FileSet = org.apache.tools.ant.types.FileSet

  def apply()(implicit P: Project) = new FileSet() {
    setProject(AntProject())
  }
  def apply(dir: String, include: Seq[String])(implicit P: Project) = new FileSet() {
    setProject(AntProject())
    setDir(Path(dir))
  }

}