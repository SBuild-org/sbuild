package de.tototec.sbuild.ant.tasks

import org.apache.tools.ant.taskdefs.Zip
import de.tototec.sbuild.Project
import de.tototec.sbuild.ant.AntProject
import java.io.File
import org.apache.tools.ant.types.{ FileSet => AFileSet }

object AntZip {
  def apply(destFile: File = null,
            baseDir: File = null,
            includes: String = null,
            excludes: String = null,
            // since SBuild 0.1.3.9000
            compress: java.lang.Boolean = null,
            keepCompression: java.lang.Boolean = null,
            filesOnly: java.lang.Boolean = null,
            fileSet: AFileSet = null,
            fileSets: Seq[AFileSet] = null)(implicit _project: Project) =
    new AntZip(
      destFile = destFile,
      baseDir = baseDir,
      includes = includes,
      excludes = excludes,
      compress = compress,
      keepCompression = keepCompression,
      filesOnly = filesOnly,
      fileSet = fileSet,
      fileSets = fileSets
    ).execute
}

/**
 * Convenience wrapper for the <strong>Zip</strong> Ant task.
 */
class AntZip()(implicit _project: Project) extends Zip {
  setProject(AntProject())

  def this(
    destFile: File = null,
    baseDir: File = null,
    includes: String = null,
    excludes: String = null,
    // since SBuild 0.1.3.9000
    compress: java.lang.Boolean = null,
    keepCompression: java.lang.Boolean = null,
    filesOnly: java.lang.Boolean = null,
    fileSet: AFileSet = null,
    fileSets: Seq[AFileSet] = null)(implicit _project: Project) {
    this
    if (destFile != null) setDestFile(destFile)
    if (baseDir != null) setBasedir(baseDir)
    if (includes != null) setIncludes(includes)
    if (excludes != null) setExcludes(excludes)
    if (compress != null) setCompress(compress.booleanValue())
    if (keepCompression != null) setKeepCompression(keepCompression.booleanValue())
    if (filesOnly != null) setFilesonly(filesOnly.booleanValue())
    if (fileSet != null) addFileset(fileSet)
    if (fileSets != null && !fileSets.isEmpty) fileSets.foreach { fileSet =>
      addFileset(fileSet)
    }
  }

  def setBaseDir(baseDir: File) = setBasedir(baseDir)
  def setFilesOnly(filesOnly: Boolean) = setFilesonly(filesOnly)

}