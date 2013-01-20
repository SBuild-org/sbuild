package de.tototec.sbuild

import java.io.File
import java.text.MessageFormat

/**
 * Common superclass for specific SBuild exceptions.
 */
class SBuildException(msg: String, cause: Throwable = null, localizedMsg: String = null) extends RuntimeException(msg, cause) with BuildScriptAware {
  override def getLocalizedMessage: String = localizedMsg match {
    case null => msg
    case x => x
  }
}

trait BuildScriptAware {
  var buildScript: Option[File] = None
}

trait LocalizableSupport[E] {

  def localized(msgId: String, params: String*): E = localized(null: Throwable, msgId, params: _*)

  def localized(cause: Throwable, msgId: String, params: String*): E = {
    // TODO: actually translate
    val translated: String = msgId
    val (msg, localized) = params match {
      case Seq() => (msgId, translated)
      case x => (MessageFormat.format(msgId, x: _*), MessageFormat.format(translated, x: _*))
    }
    create(msg, cause, localized)
  }

  protected def create(msg: String, cause: Throwable, localizedMsg: String): E

}

object InvalidCommandlineException extends LocalizableSupport[InvalidCommandlineException] {
  override def create(msg: String, cause: Throwable, localizedMsg: String): InvalidCommandlineException =
    new InvalidCommandlineException(msg, cause, localizedMsg)
}

/**
 * An invalid commandline was given.
 */
class InvalidCommandlineException(msg: String, cause: Throwable = null, localizedMsg: String = null)
    extends SBuildException(msg, cause, localizedMsg) {
}

object ExecutionFailedException extends LocalizableSupport[ExecutionFailedException] {
  override def create(msg: String, cause: Throwable, localizedMsg: String): ExecutionFailedException =
    new ExecutionFailedException(msg, cause, localizedMsg)
}

/**
 * The execution of an target, which was defined in the project file, failed.
 */
class ExecutionFailedException(msg: String, cause: Throwable = null, localizedMsg: String = null)
  extends SBuildException(msg, cause, localizedMsg)

object ProjectConfigurationException extends LocalizableSupport[ProjectConfigurationException] {
  override def create(msg: String, cause: Throwable, localizedMsg: String): ProjectConfigurationException =
    new ProjectConfigurationException(msg, cause, localizedMsg)
}
/**
 * A error was detected while parsing and/or initializing the project.
 */
class ProjectConfigurationException(msg: String, cause: Throwable = null, localizedMsg: String = null)
  extends SBuildException(msg, cause, localizedMsg)

object UnsupportedSchemeException extends LocalizableSupport[UnsupportedSchemeException] {
  override def create(msg: String, cause: Throwable, localizedMsg: String): UnsupportedSchemeException =
    new UnsupportedSchemeException(msg, cause, localizedMsg)
}
/**
 * An unsupported scheme was used in a target or dependency.
 * Usual reasons are typos or forgotten scheme handler registrations.
 */
class UnsupportedSchemeException(msg: String, cause: Throwable = null, localizedMsg: String = null)
  extends SBuildException(msg, cause, localizedMsg)

object TargetNotFoundException extends LocalizableSupport[TargetNotFoundException] {
  override def create(msg: String, cause: Throwable, localizedMsg: String): TargetNotFoundException =
    new TargetNotFoundException(msg, cause, localizedMsg)
}
/**
 * An unknown target was requested (on command line or as a dependency).
 */
class TargetNotFoundException(msg: String, cause: Throwable = null, localizedMsg: String = null)
  extends SBuildException(msg, cause, localizedMsg)

object MissingConfigurationException extends LocalizableSupport[MissingConfigurationException] {
  override def create(msg: String, cause: Throwable, localizedMsg: String): MissingConfigurationException =
    new MissingConfigurationException(msg, cause, localizedMsg)
}
/**
 * An required but not-configured property was accessed. A re-configuration is neede.
 */
class MissingConfigurationException(msg: String, cause: Throwable = null, localizedMsg: String = null)
  extends SBuildException(msg, cause, localizedMsg)
