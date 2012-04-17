package de.tototec.sbuild.ant.tasks

import org.apache.tools.ant.taskdefs.Zip
import de.tototec.sbuild.Project
import de.tototec.sbuild.ant.AntProject
import java.io.File

object AntZip {
  def apply(destFile: File = null,
            baseDir: File = null,
            includes: String = null,
            excludes: String = null)(implicit _project: Project) =
    new AntZip(
      destFile = destFile,
      baseDir = baseDir,
      includes = includes,
      excludes = excludes
    ).execute
}

class AntZip()(implicit _project: Project) extends Zip {
  setProject(AntProject())

  def this(
    destFile: File,
    baseDir: File,
    includes: String = null,
    excludes: String = null)(implicit _project: Project) {
    this
    if (destFile != null) setDestFile(destFile)
    if (baseDir != null) setBasedir(baseDir)
    if (includes != null) setIncludes(includes)
    if (excludes != null) setExcludes(excludes)
  }

  def setBaseDir(baseDir: File) = setBasedir(baseDir)

}