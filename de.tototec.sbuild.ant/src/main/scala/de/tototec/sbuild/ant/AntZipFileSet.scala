package de.tototec.sbuild.ant

import de.tototec.sbuild.Project
import java.io.File
import org.apache.tools.ant.Location
import org.apache.tools.ant.types.ZipFileSet

// Since SBuild 0.3.1.9000
object AntZipFileSet {
  def apply(dir: File = null,
            file: File = null,
            prefix: String = null,
            fullPath: String = null,
            includes: String = null,
            excludes: String = null,
            defaultExcludes: java.lang.Boolean = null,
            caseSensitive: java.lang.Boolean = null,
            followSymlinks: java.lang.Boolean = null,
            maxLevelOfSymlinks: java.lang.Integer = null,
            errorOnMissingDir: java.lang.Boolean = null)(implicit _project: Project) =
    new AntZipFileSet(
      dir = dir,
      file = file,
      prefix = prefix,
      fullPath = fullPath,
      includes = includes,
      excludes = excludes,
      defaultExcludes = defaultExcludes,
      caseSensitive = caseSensitive,
      followSymlinks = followSymlinks,
      maxLevelOfSymlinks = maxLevelOfSymlinks,
      errorOnMissingDir = errorOnMissingDir
    )
}

// Since SBuild 0.3.1.9000
class AntZipFileSet()(implicit _project: Project) extends ZipFileSet {
  setProject(AntProject())

  def this(
    dir: File = null,
    file: File = null,
    prefix: String = null,
    fullPath: String = null,
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
    if (prefix != null) setPrefix(prefix)
    if (fullPath != null) setFullpath(fullPath)
    if (includes != null) setIncludes(includes)
    if (excludes != null) setExcludes(excludes)
    if (defaultExcludes != null) setDefaultexcludes(defaultExcludes.booleanValue)
    if (caseSensitive != null) setCaseSensitive(caseSensitive.booleanValue)
    if (followSymlinks != null) setFollowSymlinks(followSymlinks.booleanValue)
    if (maxLevelOfSymlinks != null) setMaxLevelsOfSymlinks(maxLevelOfSymlinks.intValue)
    if (errorOnMissingDir != null) setErrorOnMissingDir(errorOnMissingDir.booleanValue)
  }

  def setFullPath(fullPath: String) = setFullpath(fullPath)

}