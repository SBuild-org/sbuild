package de.tototec.sbuild.ant

import de.tototec.sbuild.Project
import java.io.File
import de.tototec.sbuild.Path
import org.apache.tools.ant.types.FileSet

object AntFileSet {
  def apply(dir: File = null,
            file: File = null,
            includes: String = null,
            excludes: String = null,
            defaultExcludes: java.lang.Boolean = null,
            caseSensitive: java.lang.Boolean = null,
            followSymlinks: java.lang.Boolean = null,
            maxLevelOfSymlinks: java.lang.Integer = null,
            errorOnMissingDir: java.lang.Boolean = null)(implicit _project: Project) =
    new AntFileSet(
      dir = dir,
      file = file,
      includes = includes,
      excludes = excludes,
      defaultExcludes = defaultExcludes,
      caseSensitive = caseSensitive,
      followSymlinks = followSymlinks,
      maxLevelOfSymlinks = maxLevelOfSymlinks,
      errorOnMissingDir = errorOnMissingDir
    )
}

class AntFileSet()(implicit _project: Project) extends FileSet {
  setProject(AntProject())

  def this(dir: File = null,
           file: File = null,
           includes: String = null,
           excludes: String = null,
           defaultExcludes: java.lang.Boolean = null,
           caseSensitive: java.lang.Boolean = null,
           followSymlinks: java.lang.Boolean = null,
           maxLevelOfSymlinks: java.lang.Integer = null,
           errorOnMissingDir: java.lang.Boolean = null)(implicit _project: Project) {
    this
    if (dir != null) setDir(dir)
    if (file != null) setFile(file)
    if (includes != null) setIncludes(includes)
    if (excludes != null) setExcludes(excludes)
    if (defaultExcludes != null) setDefaultexcludes(defaultExcludes.booleanValue)
    if (caseSensitive != null) setCaseSensitive(caseSensitive.booleanValue)
    if (followSymlinks != null) setFollowSymlinks(followSymlinks.booleanValue)
    if (maxLevelOfSymlinks != null) setMaxLevelsOfSymlinks(maxLevelOfSymlinks.intValue)
    if (errorOnMissingDir != null) setErrorOnMissingDir(errorOnMissingDir.booleanValue)
  }
}