package de.tototec.sbuild.ant.tasks

import java.io.File
import de.tototec.sbuild.Project
import de.tototec.sbuild.ant.AntProject
import org.apache.tools.ant.taskdefs.Chmod
import org.apache.tools.ant.types.EnumeratedAttribute
import org.apache.tools.ant.taskdefs.ExecuteOn.FileDirBoth

object AntChmod {
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

class AntChmod()(implicit _project: Project) extends Chmod {
  setProject(AntProject())

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

  def setDefaultExcludes(defaultExcludes: Boolean) = setDefaultexcludes(defaultExcludes)

}