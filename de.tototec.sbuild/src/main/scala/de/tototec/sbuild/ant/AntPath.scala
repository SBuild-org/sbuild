package de.tototec.sbuild.ant

import de.tototec.sbuild.Project
import java.io.File
import de.tototec.sbuild.Path
import de.tototec.sbuild.TargetRef
import de.tototec.sbuild.TargetRefs

object AntPath {
  type AntPath = org.apache.tools.ant.types.Path

  def apply(targetRefs: TargetRefs)(implicit proj: Project): org.apache.tools.ant.types.Path =
    new org.apache.tools.ant.types.Path(AntProject()(proj)) {
      targetRefs.targetRefs.foreach { targetRef =>
        setLocation(proj.uniqueTargetFile(targetRef).file)
      }
    }
  def apply(targetRef: TargetRef)(implicit proj: Project): org.apache.tools.ant.types.Path =
    new org.apache.tools.ant.types.Path(AntProject()(proj)) {
      setLocation(proj.uniqueTargetFile(targetRef).file)
    }
  def apply(file: String)(implicit proj: Project): org.apache.tools.ant.types.Path =
    new org.apache.tools.ant.types.Path(AntProject()(proj)) {
      setLocation(Path(file)(proj))
    }
  def apply(file: File)(implicit proj: Project): org.apache.tools.ant.types.Path =
    new org.apache.tools.ant.types.Path(AntProject()(proj)) {
      setLocation(Path(file.getPath())(proj))
    }
}
