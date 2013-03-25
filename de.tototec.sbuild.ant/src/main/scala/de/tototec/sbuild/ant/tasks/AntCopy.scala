package de.tototec.sbuild.ant.tasks

import de.tototec.sbuild.Project
import org.apache.tools.ant.taskdefs.Copy
import de.tototec.sbuild.ant.AntProject
import java.io.File
import org.apache.tools.ant.types.FileSet

/**
 * Wrapper for the [[http://ant.apache.org/manual/Tasks/copy.html Ant Copy task]].
 */
object AntCopy {

  /**
   * Creates, configures and executes an Ant Copy task.
   *
   * For parameter documentation see the constructor of [[AntCopy]].
   */
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

/**
 * Wrapper for the [[http://ant.apache.org/manual/Tasks/copy.html Ant Copy task]].
 *
 * Copy a file or a directory or a set of files and directories.
 *
 *
 */
class AntCopy()(implicit _project: Project) extends Copy {
  setProject(AntProject())

  /**
   * Creates and configures a copy task.
   *
   * @param file The file to copy.
   * @param toFile The target file to copy to.
   * @param toDir The target directory to copy to.
   * @param preserveLastModified Give the copied files the same last modified time as the original source file.
   * @param filtering Indicates whether token filtering uses the global build-file filters (of Ant) should take place during the copy.
   * @param overwrite Overwrite existing files even if the destination files are newer.
   * @param force Overwrite read-only destination files.
   * @param flatten Ignore the directory structure of the source files, and copy all files into the directory specified by the `toDir` parameter.
   * @param verbose Log the files that are being copied.
   * @param includeEmptyDirs Copy any empty directories included in the FileSet(s).
   * @param quiet
   *   If `true` and `failOnError` is `false`, then do not log a warning message when the file to copy does not exist
   *   or one of the nested file sets points to a directory that does not exist
   *   or an error occurs while copying.
   * @param enableMultipleMappings If `true` the task will process to all the mappings for a given source path.
   * @param encoding The encoding to assume when filter-copying the files.
   * @param outputEncoding the encoding to use when writing the files.
   * @param granularity The number of milliseconds leeway to give before deciding a file is out of date.
   * @param fileSet A [[org.apache.tools.ant.types.FileSet]] used to select groups of files to copy.
   * @param fileSets [[org.apache.tools.ant.types.FileSet]]'s used to select groups of files to copy.
   *
   */
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
    // since 0.1.3.9000
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
    if (fileSet != null) addFileset(fileSet)
  }

  /** Set the target directory to copy to. */
  def setToDir(toDir: File) = setTodir(toDir)
  /** Set the target file to copy to. */
  def setToFile(toFile: File) = setTofile(toFile)

} 