package de.tototec.sbuild.ant.tasks

import java.io.File

import org.apache.tools.ant.taskdefs.Jar

import de.tototec.sbuild.ant.AntProject
import de.tototec.sbuild.Path
import de.tototec.sbuild.Project

object AntJar {
  def apply(destFile: File,
            baseDir: File,
            manifest: File = null,
            includes: String = null,
            excludes: String = null)(implicit _project: Project) =
    new AntJar(
      destFile = destFile,
      baseDir = baseDir,
      manifest = manifest,
      includes = includes,
      excludes = excludes
    ).execute
}

class AntJar()(implicit _project: Project) extends Jar {
  setProject(AntProject())

  def this(destFile: File,
           baseDir: File,
           manifest: File = null,
           includes: String = null,
           excludes: String = null)(implicit _project: Project) {
    this
    setDestFile(destFile)
    setBasedir(baseDir)
    if (manifest != null) setManifest(manifest)
    if (includes != null) setIncludes(includes)
    if (excludes != null) setExcludes(excludes)
  }

  override def setDestFile(destFile: File) = super.setDestFile(Path(destFile.getPath))
  override def setBasedir(basedir: File) = super.setBasedir(Path(basedir.getPath))
  override def setManifest(manifest: File) = super.setManifest(Path(manifest.getPath))

  def setBaseDir(baseDir: File) = setBasedir(baseDir)

}