package org.sbuild.ant

import org.apache.tools.ant.types.FileList
import org.sbuild.Project
import java.io.File
import org.apache.tools.ant.Location

object AntFileList {
  def apply(dir: File = null,
            files: String = null)(implicit _project: Project) =
    new AntFileList(
      dir = dir,
      files = files
    )
}

class AntFileList()(implicit _project: Project) extends FileList {
  setProject(AntProject())

  def this(dir: File = null,
           files: String = null)(implicit _project: Project) {
    this
    if (dir != null) setDir(dir)
    if (files != null) setFiles(files)

  }

}