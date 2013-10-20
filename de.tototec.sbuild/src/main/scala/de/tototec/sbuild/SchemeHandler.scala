package de.tototec.sbuild

import de.tototec.sbuild.SchemeHandler.SchemeContext

/**
 * Translates a target name into another target name
 */
trait SchemeHandler {

  /**
   * The resulting target name (path) this target resolves to.
   */
  def localPath(schemeContext: SchemeContext): String
}

/**
 * Register a SchemeHandler under a scheme qualifier into the current project.
 */
object SchemeHandler {
  /**
   * @param scheme The scheme of the target.
   * @param path The given target name without the scheme.
   */
  case class SchemeContext(scheme: String, path: String) {
    def fullName = scheme + ":" + path
  }

  def apply(scheme: String, handler: SchemeHandler)(implicit project: Project) =
    project.registerSchemeHandler(scheme, handler)

  // since 0.4.0.9001
  def replace(scheme: String, handler: SchemeHandler)(implicit project: Project) =
    project.replaceSchemeHandler(scheme, handler)
}

/**
 * A SchemeHandler, that also resolves the representing target.
 *  with a built-in target scheme.
 * The localPath of such schemes should translate into a built-in target scheme, either "file" or "phony".
 * If SBuild decides, that the virtual target needs to be executed (is not up-to-date),
 * [[de.tototec.sbuild.SchemeResolver#resolve(String)]] will be called.
 *
 */
trait SchemeResolver extends SchemeHandler {
  /**
   * Actually resolve the dependency/target.
   */
  def resolve(schemeContext: SchemeContext, targetContext: TargetContext)
}

trait SchemeResolverWithDependencies extends SchemeResolver {
  /**
   * Return the dependencies required to be resolved when resolving the given path.
   * Please note, that the return value of this method needs to be stable for the same path,
   * as it is evaluated at configuration time, not at resolving time.
   */
  def dependsOn(schemeContext: SchemeContext): TargetRefs
}

@deprecated("Use SchemeResolverWithDependencies instead.", "0.4.1")
trait SchemeHandlerWithDependencies extends SchemeResolverWithDependencies

/**
 * An internal marker interface.
 * Currently used to denote a scheme handler, that should work more silently, e.g. the default "scan:" handler.
 *
 */
trait TransparentSchemeResolver extends SchemeResolver

/**
 * An internal marker interface.
 * Currently used to denote a scheme resolver,
 * that do not change other files except the target file (localPath) and the attached files (TargetContext).
 */
trait SideeffectFreeSchemeResolver extends SchemeResolver
