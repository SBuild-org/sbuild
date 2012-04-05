package de.tototec.sbuild.ant

import de.tototec.sbuild.Project
import java.io.File
import de.tototec.sbuild.Path

object AntFileSet {
  type FileSet = org.apache.tools.ant.types.FileSet

  def apply()(implicit P: Project) = new FileSet() {
    setProject(AntProject())
  }
  def apply(dir: String, include: Seq[String] = Seq())(implicit P: Project) = new FileSet() {
    setProject(AntProject())
    setDir(Path(dir))
    include match {
      case Seq() =>
      case s => setIncludes(s.mkString(" "))
    }
  }

}
