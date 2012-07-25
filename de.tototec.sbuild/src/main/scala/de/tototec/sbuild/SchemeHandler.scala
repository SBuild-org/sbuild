package de.tototec.sbuild

/**
 * Translates some kind of path into a local path suitable as a virtual target name.
 * If SBuild decides, that the virtual target needs to be executed (is not up-to-date),
 * (@link #resolve(String)} will be called.
 * 
 */
trait SchemeHandler {
  // shold return something starting with "file:" or "phony:"
  def localPath(path: String): String
  def resolve(path: String): Option[Throwable]
}

/**
 * Register a SchemeHandler under a scheme qualifier into the current project.
 */
object SchemeHandler {
  def apply(scheme: String, handler: SchemeHandler)(implicit project: Project) =
    project.registerSchemeHandler(scheme, handler)
}