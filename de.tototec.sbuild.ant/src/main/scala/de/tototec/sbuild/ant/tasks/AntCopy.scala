package de.tototec.sbuild.ant.tasks

import de.tototec.sbuild.Project
import org.apache.tools.ant.taskdefs.Copy
import de.tototec.sbuild.ant.AntProject
import java.io.File
import org.apache.tools.ant.types.FileSet

object AntCopy {
  def apply(
    file: File = null,
    toFile: File = null,
    toDir: File = null,
    preserveLastModified: java.lang.Boolean = null,
    filtering: java.lang.Boolean = null,
    overwrite: java.lang.Boolean = null,
    force: java.lang.Boolean = null,
    flatten: java.lang.Boolean = null,
    verbose: java.lang.Boolean = null,
    includeEmptyDirs: java.lang.Boolean = null,
    quiet: java.lang.Boolean = null,
    enableMultipleMappings: java.lang.Boolean = null,
    encoding: String = null,
    outputEncoding: String = null,
    granularity: java.lang.Long = null,
    // since 0.1.0.9001
    fileSets: Seq[FileSet] = null,
    // since 0.1.3.9000
    fileSet: FileSet = null)(implicit _project: Project) =
    new AntCopy(
      file = file,
      toFile = toFile,
      toDir = toDir,
      preserveLastModified = preserveLastModified,
      filtering = filtering,
      overwrite = overwrite,
      force = force,
      flatten = flatten,
      verbose = verbose,
      includeEmptyDirs = includeEmptyDirs,
      quiet = quiet,
      enableMultipleMappings = enableMultipleMappings,
      encoding = encoding,
      outputEncoding = outputEncoding,
      granularity = granularity,
      fileSets = fileSets,
      fileSet = fileSet
    ).execute
}

class AntCopy()(implicit _project: Project) extends Copy {
  setProject(AntProject())

  def this(
    file: File = null,
    toFile: File = null,
    toDir: File = null,
    preserveLastModified: java.lang.Boolean = null,
    filtering: java.lang.Boolean = null,
    overwrite: java.lang.Boolean = null,
    force: java.lang.Boolean = null,
    flatten: java.lang.Boolean = null,
    verbose: java.lang.Boolean = null,
    includeEmptyDirs: java.lang.Boolean = null,
    quiet: java.lang.Boolean = null,
    enableMultipleMappings: java.lang.Boolean = null,
    encoding: String = null,
    outputEncoding: String = null,
    granularity: java.lang.Long = null,
    // since 0.1.0.9001
    fileSets: Seq[FileSet] = null,
     // since 0.1.2.9000
    fileSet: FileSet = null)(implicit _project: Project) {
    this
    if (file != null) setFile(file)
    if (toFile != null) setTofile(toFile)
    if (toDir != null) setTodir(toDir)
    if (preserveLastModified != null) setPreserveLastModified(preserveLastModified.booleanValue)
    if (filtering != null) setFiltering(filtering.booleanValue)
    if (overwrite != null) setOverwrite(overwrite.booleanValue)
    if (force != null) setOverwrite(overwrite.booleanValue)
    if (flatten != null) setFlatten(flatten.booleanValue)
    if (verbose != null) setVerbose(verbose.booleanValue)
    if (includeEmptyDirs != null) setVerbose(includeEmptyDirs.booleanValue)
    if (quiet != null) setQuiet(quiet.booleanValue)
    if (enableMultipleMappings != null) setQuiet(enableMultipleMappings.booleanValue)
    if (encoding != null) setEncoding(encoding)
    if (outputEncoding != null) setOutputEncoding(outputEncoding)
    if (granularity != null) setGranularity(granularity)
    if (fileSets != null) fileSets.foreach { fileSet =>
      addFileset(fileSet)
    }
    if (fileSets != null) addFileset(fileSet)
  }

  def setToDir(toDir: File) = setTodir(toDir)
  def setToFile(toFile: File) = setTofile(toFile)

} 