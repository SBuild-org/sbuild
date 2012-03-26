package de.tototec.sbuild

trait SchemeHandler {
  // shold return something starting with "file:" or "phony:"
  def localPath(path: String): String
  def resolve(path: String): Option[Throwable]
}

object SchemeHandler {
  def apply(scheme: String, handler: SchemeHandler)(implicit project: Project) =
    project.registerSchemeHandler(scheme, handler)
}