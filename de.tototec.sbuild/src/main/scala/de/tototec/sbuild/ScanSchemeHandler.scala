package de.tototec.sbuild

import java.io.File

class ScanSchemeHandler(implicit project: Project)
    extends SchemeResolver 
    with TransparentSchemeResolver {

  override def localPath(path: String): String = s"phony:scan-${path}"

  override def resolve(path: String, targetContext: TargetContext): Unit = {
    // Ensure, we always report a last modified, even, if we don't find any file
    targetContext.targetLastModified = 1
    scan(path, targetContext).foreach { f => targetContext.attachFile(f) }
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

    Util.recursiveListFiles(dir, regex)
  }

}