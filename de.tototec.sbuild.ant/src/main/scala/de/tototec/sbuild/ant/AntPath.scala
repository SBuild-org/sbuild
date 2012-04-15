package de.tototec.sbuild.ant

import de.tototec.sbuild.Project
import java.io.File
import de.tototec.sbuild.Path
import de.tototec.sbuild.TargetRef
import de.tototec.sbuild.TargetRefs

object AntPath {

  def apply(targetRefs: TargetRefs)(implicit proj: Project): AntPath = {
    val antPath = new AntPath()
    targetRefs.targetRefs.foreach { targetRef =>
      antPath.setLocation(proj.uniqueTargetFile(targetRef).file)
    }
    antPath
  }

  def apply(targetRef: TargetRef)(implicit proj: Project): AntPath =
    new AntPath(proj.uniqueTargetFile(targetRef).file)

  def apply(file: String)(implicit proj: Project): AntPath = new AntPath(Path(file))
  def apply(files: Array[String])(implicit proj: Project): AntPath = new AntPath(files.map { f => Path(f) })

  def apply(file: File)(implicit proj: Project): AntPath = new AntPath(file)
  def apply(files: Array[File])(implicit proj: Project): AntPath = new AntPath(files)
}

class AntPath()(implicit _project: Project) extends org.apache.tools.ant.types.Path(AntProject()) {

  def this(location: File = null)(implicit project: Project) {
    this
    if (location != null) setLocation(location)
  }

  def this(locations: Array[File])(implicit _project: Project) {
    this
    locations.foreach { loc =>
      setLocation(loc)
    }
  }

  override def setLocation(location: File) = super.setLocation(Path(location.getPath))
}