package de.tototec.sbuild.ant.tasks

import de.tototec.sbuild.Path
import de.tototec.sbuild.Project
import de.tototec.sbuild.ant.AntProject
import java.io.File
import org.apache.tools.ant.taskdefs.Delete

object AntDelete {

  def apply(file: File = null,
            dir: File = null,
            includes: String = null,
            excludes: String = null)(implicit proj: Project) =
    new AntDelete(
      file = file,
      dir = dir,
      includes = includes,
      excludes = excludes
    ).execute

}

class AntDelete()(implicit _project: Project) extends Delete {
  setProject(AntProject())

  def this(file: File = null,
           dir: File = null,
           includes: String = null,
           excludes: String = null)(implicit proj: Project) {
    this
    if (file != null) setFile(file)
    if (dir != null) setDir(dir)
    if (includes != null) setIncludes(includes)
    if (excludes != null) setExcludes(excludes)
  }

  override def setFile(file: File) = super.setFile(Path(file.getPath))
  override def setDir(dir: File) = super.setDir(Path(dir.getPath))

}

