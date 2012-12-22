package de.tototec.sbuild.ant

import de.tototec.sbuild.Project
import java.io.File
import de.tototec.sbuild.Path
import de.tototec.sbuild.TargetRef
import de.tototec.sbuild.TargetRefs

object AntPath {

  def apply(location: File = null,
            locations: Seq[File] = null,
            path: String = null,
            paths: Seq[String] = null)(implicit project: Project) =
    new AntPath(
      location = location,
      locations = locations,
      path = path,
      paths = paths
    )

  def apply(targetRefs: TargetRefs)(implicit _project: Project): AntPath =
    new AntPath(locations = targetRefs.targetRefs.map { targetRef =>
      _project.uniqueTargetFile(targetRef).file
    })

  def apply(targetRef: TargetRef)(implicit proj: Project): AntPath =
    new AntPath(location = proj.uniqueTargetFile(targetRef).file)

}

class AntPath()(implicit _project: Project) extends org.apache.tools.ant.types.Path(AntProject()) {

  def this(location: File = null,
           locations: Seq[File] = null,
           path: String = null,
           paths: Seq[String] = null)(implicit project: Project) {
    this
    if (location != null) setLocation(location)
    if (locations != null) locations.foreach { loc => setLocation(loc) }
    if (path != null) setPath(path)
    if (paths != null) paths.foreach { path => setPath(path) }
  }

}