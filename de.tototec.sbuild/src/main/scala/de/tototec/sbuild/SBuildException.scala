package de.tototec.sbuild

import java.io.File
import java.text.MessageFormat
import scala.reflect.ClassTag
import de.tototec.sbuild.internal.I18n

/**
 * Common superclass for specific SBuild exceptions.
 */
class SBuildException(msg: String, cause: Throwable = null, localizedMsg: String = null)
    extends RuntimeException(msg, cause)
    with BuildScriptAware
    with TargetAware {
  override def getLocalizedMessage: String = localizedMsg match {
    case null => msg
    case x => x
  }
}

trait BuildScriptAware {
  var buildScript: Option[File] = None
}

trait TargetAware {
  var targetName: Option[String] = None
}

/**
 * An invalid commandline was given.
 */
class InvalidCommandlineException(msg: String, cause: Throwable = null, localizedMsg: String = null)
    extends SBuildException(msg, cause, localizedMsg) {
}

/**
 * The execution of an target, which was defined in the project file, failed.
 */
class ExecutionFailedException(msg: String, cause: Throwable = null, localizedMsg: String = null)
  extends SBuildException(msg, cause, localizedMsg)

/**
 * A error was detected while parsing and/or initializing the project.
 */
class ProjectConfigurationException(msg: String, cause: Throwable = null, localizedMsg: String = null)
  extends SBuildException(msg, cause, localizedMsg)

/**
 * An unsupported scheme was used in a target or dependency.
 * Usual reasons are typos or forgotten scheme handler registrations.
 */
class UnsupportedSchemeException(msg: String, cause: Throwable = null, localizedMsg: String = null)
  extends SBuildException(msg, cause, localizedMsg)

/**
 * An unknown target was requested (on command line or as a dependency).
 */
class TargetNotFoundException(msg: String, cause: Throwable = null, localizedMsg: String = null)
  extends SBuildException(msg, cause, localizedMsg)

/**
 * An required but not-configured property was accessed. A re-configuration is neede.
 */
class MissingConfigurationException(msg: String, cause: Throwable = null, localizedMsg: String = null)
  extends SBuildException(msg, cause, localizedMsg)

/**
 * Invalid use of SBuild API.
 */
class InvalidApiUsageException(msg: String, cause: Throwable = null, localizedMsg: String = null)
  extends ProjectConfigurationException(msg, cause, localizedMsg)

/**
 * Invalid use of SBuild API.
 */
class BuildfileCompilationException(msg: String, cause: Throwable = null, localizedMsg: String = null)
  extends ProjectConfigurationException(msg, cause, localizedMsg)

