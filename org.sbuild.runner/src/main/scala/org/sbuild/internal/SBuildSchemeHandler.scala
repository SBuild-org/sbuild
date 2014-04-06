package org.sbuild.internal

import org.sbuild.SchemeHandler
import org.sbuild.TargetContext
import org.sbuild.TargetNotFoundException
import org.sbuild.TransparentSchemeResolver

/**
 * This SchemeHandler provides a "sbuild:" scheme, to provide some internal pseudo dependencies.
 *
 *
 */
class SBuildSchemeHandler(projectLastModified: Long)
    extends SchemeHandler
    with TransparentSchemeResolver {

  override def localPath(schemeContext: SchemeHandler.SchemeContext): String = {
    schemeContext.path match {
      case "project" | "force" => s"phony:${schemeContext.fullName}"
      case _ =>
        throw new TargetNotFoundException("Unsupported path in dependency: " + schemeContext.fullName)
    }
  }

  override def resolve(schemeContext: SchemeHandler.SchemeContext, targetContext: TargetContext): Unit = {
    schemeContext.path match {
      case "project" =>
        targetContext.targetLastModified = projectLastModified
      case "force" =>
        targetContext.targetLastModified = System.currentTimeMillis

      case _ =>
        throw new TargetNotFoundException("Unsupported path in dependency: " + schemeContext.fullName)
    }
  }

}