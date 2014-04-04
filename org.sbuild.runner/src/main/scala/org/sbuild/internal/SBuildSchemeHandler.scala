package org.sbuild.internal

import java.io.File

import org.sbuild.SchemeHandler
import org.sbuild.TargetNotFoundException
import org.sbuild.toRichFile
import org.sbuild.SBuildVersion

/**
 * This SchemeHandler provides a "sbuild:" scheme, to provide some internal pseudo dependencies.
 *
 *
 */
class SBuildSchemeHandler(sbuildHomeDir: Option[File]) extends SchemeHandler {

  override def localPath(schemeContext: SchemeHandler.SchemeContext): String = {
    (sbuildHomeDir, schemeContext.path) match {
      // case "projectConfig" =>
      case (Some(dir), "org.sbuild.jar") =>
        (dir / "lib" / s"org.sbuild-${SBuildVersion.version}.jar").getPath
      case (Some(dir), "org.sbiuld.runner.jar") =>
        (dir / "lib" / s"org.sbuild.runner-${SBuildVersion.version}.jar").getPath
      case _ =>
        throw new TargetNotFoundException("Unsupported path in dependency: " + schemeContext.fullName)
    }
  }
}