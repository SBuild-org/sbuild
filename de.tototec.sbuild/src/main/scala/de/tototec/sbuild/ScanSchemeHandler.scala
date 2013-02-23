package de.tototec.sbuild

import java.io.File

class ScanSchemeHandler(implicit project: Project)
    extends SchemeResolver //    extends SchemeHandlerWithDependencies
    with TransparentSchemeResolver {

  override def localPath(path: String): String = s"phony:scan-${path}"

  override def resolve(path: String, targetContext: TargetContext): Unit = {
    // Ensure, we always report a last modified, even, if we don't find any file
    targetContext.targetLastModified = 1
    scan(path).foreach { f => targetContext.attachFile(f) }
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