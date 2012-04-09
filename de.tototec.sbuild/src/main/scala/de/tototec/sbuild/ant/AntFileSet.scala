package de.tototec.sbuild.ant

import de.tototec.sbuild.Project
import java.io.File
import de.tototec.sbuild.Path
import org.apache.tools.ant.types.FileSet

object AntFileSet {
  type AntFileSet = org.apache.tools.ant.types.FileSet

  def apply()(implicit P: Project) = new FileSet() {
    setProject(AntProject())
  }

  def apply(dir: String,
            file: File = null,
            include: Seq[String] = Seq(),
            exclude: Seq[String] = Seq())(implicit P: Project) =
    new FileSet() {
      setProject(AntProject())
      setDir(Path(dir))
      file match {
        case null =>
        case f => setFile(f)
      }
      include match {
        case Seq() =>
        case s => setIncludes(s.mkString(","))
      }
      exclude match {
        case Seq() =>
        case s => setExcludes(s.mkString(","))
      }
    }

}
