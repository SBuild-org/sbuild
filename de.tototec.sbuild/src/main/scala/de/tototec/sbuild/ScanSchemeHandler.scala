package de.tototec.sbuild

import java.io.File

class ScanSchemeHandler(implicit project: Project)
    extends SchemeHandler //    extends SchemeHandlerWithDependencies
    with TransparentSchemeHandler
    {

  override def localPath(path: String): String = s"phony:scan-${path}"

  override def resolve(path: String, targetContext: TargetContext): Unit =
    scan(path).foreach {
      f =>
        targetContext.attachFile(f)
        targetContext.targetLastModified = f.lastModified
    }

  //  override def dependsOn(path: String): TargetRefs =
  //    if (path.contains(";asDependencies"))
  //      scan(path).map(f => TargetRef(f)).toSeq
  //    else TargetRefs()

  def scan(path: String): Array[File] = {
    // TODO: parse path pattern, for now, only a simple regex
    // path := dir [ ';' 'asDependencies' ] [ ';' 'regex=' regex ]

    val nPath =
      if (path.contains(";asDependencies"))
        path.replaceFirst(";asDependencies", "")
      else path

    val (dir, regex) = nPath.split(";", 2) match {
      case Array(dir) =>
        (Path(dir), ".*".r)
      case Array(dir, regex) =>
        (Path(dir), regex.r)
    }

    Util.recursiveListFiles(dir, regex)
  }

}