package de.tototec.sbuild

import java.io.File
import de.tototec.sbuild.SchemeHandler.SchemeContext

/**
 * Scans a directory for files, recursiv.
 *
 * An optional regular expression can be used, to filter found files by name.
 *
 * Syntax:
 * {{{
 * "scan:src;regex=.*\.java"
 * }}}
 */
class ScanSchemeHandler(implicit project: Project)
    extends SchemeResolver
    with TransparentSchemeResolver
    with SideeffectFreeSchemeResolver {

  override def localPath(schemeCtx: SchemeContext): String = s"phony:${schemeCtx.scheme}:${schemeCtx.path}"

  override def resolve(schemeCtx: SchemeContext, targetContext: TargetContext): Unit = {
    // Ensure, we always report a last modified, even, if we don't find any file
    targetContext.targetLastModified = 1
    scan(schemeCtx.path, targetContext).foreach { f => targetContext.attachFile(f) }
  }

  def scan(path: String, targetContext: TargetContext): Array[File] = {
    // TODO: parse path pattern, for now, only a simple regex
    // path := dir [ ';' 'asDependencies' ] [ ';' 'regex=' regex ]

    val nPath =
      if (path.contains(";asDependencies"))
        path.replaceFirst(";asDependencies", "")
      else path

    val (dir, regex) = nPath.split(";", 2) match {
      case Array(dir) =>
        (Path(dir), ".*".r)
      case Array(dir, regex) if regex.startsWith("regex=") =>
        (Path(dir), regex.substring(6).r)
      case _ =>
        val ex = new ProjectConfigurationException("Unsupported syntax used in target/depenency: " + targetContext.name)
        ex.buildScript = Some(targetContext.project.projectFile)
        throw ex
    }

    Util.recursiveListFiles(dir, regex, log = project.log)
  }

}