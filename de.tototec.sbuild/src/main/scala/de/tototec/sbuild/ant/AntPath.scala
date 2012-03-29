package de.tototec.sbuild.ant

import de.tototec.sbuild.Project
import java.io.File
import de.tototec.sbuild.Path

object AntPath {
  def apply(file: String)(implicit proj: Project): org.apache.tools.ant.types.Path =
    new org.apache.tools.ant.types.Path(AntProject()(proj)) {
      setLocation(Path(file)(proj))
    }
  def apply(file: File)(implicit proj: Project): org.apache.tools.ant.types.Path =
    new org.apache.tools.ant.types.Path(AntProject()(proj)) {
      setLocation(Path(file.getPath())(proj))
    }
}
