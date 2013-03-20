package de.tototec.sbuild.ant.tasks

import java.io.File
import de.tototec.sbuild.Project
import de.tototec.sbuild.ant.AntProject
import org.apache.tools.ant.taskdefs.Chmod
import org.apache.tools.ant.types.EnumeratedAttribute
import org.apache.tools.ant.taskdefs.ExecuteOn.FileDirBoth

/**
 * Wrapper for the [[http://ant.apache.org/manual/Tasks/chmod.html Ant Chmod task]].
 */
object AntChmod {
  /**
   * Creates, configures and executes an Ant Chmod task.
   *
   * For parameter documentation see the constructor of [[AntChmod]].
   */
  def apply(file: File = null,
            dir: File = null,
            perm: String = null,
            includes: String = null,
            excludes: String = null,
            defaultExcludes: java.lang.Boolean = null,
            parallel: java.lang.Boolean = null,
            `type`: String = null,
            maxParallel: java.lang.Integer = null,
            verbose: java.lang.Boolean = null,
            os: String = null,
            osFamily: String = null)(implicit _project: Project) =
    new AntChmod(
      file = file,
      dir = dir,
      perm = perm,
      includes = includes,
      excludes = excludes,
      defaultExcludes = defaultExcludes,
      parallel = parallel,
      `type` = `type`,
      maxParallel = maxParallel,
      verbose = verbose,
      os = os,
      osFamily = osFamily
    ).execute
}

/**
 * Wrapper for the [[http://ant.apache.org/manual/Tasks/chmod.html Ant Chmod task]].
 *
 * Changes the permissions of a file or all files inside specified directories.
 *
 * '''Example'''
 *
 * Make the shell script `run.sh` in directory `target/runtime` readable and executable for the user, his group and all others.
 *
 * {{{
 * AntChmod(file = Path("target/runtime/run.sh"), perm="ugo+rx")
 * }}}
 *
 */
class AntChmod()(implicit _project: Project) extends Chmod {
  setProject(AntProject())

  /**
   * Creates and configures a chmod task.
   *
   * @param file The file or single directory of which the permissions must be changed.
   * @param dir The directory which holds the files whose permissions must be changed.
   * @param perm The new Permissions.
   * @param includes Comma- or space-separated list of patterns of files that must be included.
   * @param excludes Comma- or space-separated list of patterns of files that must be excluded.
   * @param defaultExcludes Indicates whether default excludes should be used or not.
   * @param parallel Process all specified files using a single `chmod` command.
   * @param type One of `"file"`, `"dir"` or `"both"`.
   * @param maxParallel Limit the amount of parallelism by passing at most this many source files at once.
   * @param verbose Whether to print a summary after execution or not.
   * @param os List of Operation Systems on which the command may be executed.
   * @param osFamily OS family as used in the os attribute.
   *
   */
  def this(file: File = null,
           dir: File = null,
           perm: String = null,
           includes: String = null,
           excludes: String = null,
           defaultExcludes: java.lang.Boolean = null,
           parallel: java.lang.Boolean = null,
           `type`: String = null,
           maxParallel: java.lang.Integer = null,
           verbose: java.lang.Boolean = null,
           os: String = null,
           osFamily: String = null)(implicit _project: Project) {
    this
    if (file != null) setFile(file)
    if (dir != null) setDir(dir)
    if (perm != null) setPerm(perm)
    if (includes != null) setIncludes(includes)
    if (excludes != null) setExcludes(excludes)
    if (defaultExcludes != null) setDefaultexcludes(defaultExcludes.booleanValue)
    if (parallel != null) setParallel(parallel.booleanValue)
    if (`type` != null) {
      val antType = new FileDirBoth()
      antType.setValue(`type`)
      setType(antType)
    }
    if (maxParallel != null) setMaxParallel(maxParallel.intValue)
    if (verbose != null) setVerbose(verbose.booleanValue)
    if (os != null) setOs(os)
    if (osFamily != null) setOsFamily(osFamily)
  }

  /** Indicates whether default excludes should be used or not. */
  def setDefaultExcludes(defaultExcludes: Boolean) = setDefaultexcludes(defaultExcludes)

}