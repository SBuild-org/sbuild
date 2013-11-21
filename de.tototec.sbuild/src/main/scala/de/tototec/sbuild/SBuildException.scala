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

trait LocalizableSupport[E] {

  @deprecated("To much code for to less gain. Please use constructor instead.", "0.6.0.9004")
  def localized(msgId: String, params: String*): E = localized(null: Throwable, msgId, params: _*)

  @deprecated("To much code for to less gain. Please use constructor instead.", "0.6.0.9004")
  def localized(cause: Throwable, msgId: String, params: String*): E = {
    // TODO: actually translate
    val translated: String = msgId
    val (msg, localized) = params match {
      case Seq() => (msgId, translated)
      case x => (MessageFormat.format(msgId, x: _*), MessageFormat.format(translated, x: _*))
    }
    create(msg, cause, localized)
  }

  @deprecated("To much code for to less gain. Please use constructor instead.", "0.6.0.9004")
  def localized[C: ClassTag](msgId: String, params: String*): E = localized(null: Throwable, msgId, params: _*)

  @deprecated("To much code for to less gain. Please use constructor instead.", "0.6.0.9004")
  def localized[C: ClassTag](cause: Throwable, msgId: String, params: String*): E = {
    val i18n = I18n[C]
    create(i18n.notr(msgId, params: _*), cause, i18n.tr(msgId, params: _*))
  }

  @deprecated("To much code for to less gain. Please use constructor instead.", "0.6.0.9004")
  protected def create(msg: String, cause: Throwable, localizedMsg: String): E

}

//////////////////////

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

//////////////////////

object ExecutionFailedException extends LocalizableSupport[ExecutionFailedException] {
  override def create(msg: String, cause: Throwable, localizedMsg: String): ExecutionFailedException =
    new ExecutionFailedException(msg, cause, localizedMsg)
}

/**
 * The execution of an target, which was defined in the project file, failed.
 */
class ExecutionFailedException(msg: String, cause: Throwable = null, localizedMsg: String = null)
  extends SBuildException(msg, cause, localizedMsg)

//////////////////////

object ProjectConfigurationException extends LocalizableSupport[ProjectConfigurationException] {
  override def create(msg: String, cause: Throwable, localizedMsg: String): ProjectConfigurationException =
    new ProjectConfigurationException(msg, cause, localizedMsg)
}
/**
 * A error was detected while parsing and/or initializing the project.
 */
class ProjectConfigurationException(msg: String, cause: Throwable = null, localizedMsg: String = null)
  extends SBuildException(msg, cause, localizedMsg)

//////////////////////

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

//////////////////////

object TargetNotFoundException extends LocalizableSupport[TargetNotFoundException] {
  override def create(msg: String, cause: Throwable, localizedMsg: String): TargetNotFoundException =
    new TargetNotFoundException(msg, cause, localizedMsg)
}
/**
 * An unknown target was requested (on command line or as a dependency).
 */
class TargetNotFoundException(msg: String, cause: Throwable = null, localizedMsg: String = null)
  extends SBuildException(msg, cause, localizedMsg)

//////////////////////

object MissingConfigurationException extends LocalizableSupport[MissingConfigurationException] {
  override def create(msg: String, cause: Throwable, localizedMsg: String): MissingConfigurationException =
    new MissingConfigurationException(msg, cause, localizedMsg)
}
/**
 * An required but not-configured property was accessed. A re-configuration is neede.
 */
class MissingConfigurationException(msg: String, cause: Throwable = null, localizedMsg: String = null)
  extends SBuildException(msg, cause, localizedMsg)

//////////////////////

object InvalidApiUsageException extends LocalizableSupport[InvalidApiUsageException] {
  override def create(msg: String, cause: Throwable, localizedMsg: String): InvalidApiUsageException =
    new InvalidApiUsageException(msg, cause, localizedMsg)
}

/**
 * Invalid use of SBuild API.
 */
class InvalidApiUsageException(msg: String, cause: Throwable = null, localizedMsg: String = null)
  extends ProjectConfigurationException(msg, cause, localizedMsg)

//////////////////////

object BuildfileCompilationException extends LocalizableSupport[BuildfileCompilationException] {
  override def create(msg: String, cause: Throwable, localizedMsg: String): BuildfileCompilationException =
    new BuildfileCompilationException(msg, cause, localizedMsg)
}

/**
 * Invalid use of SBuild API.
 */
class BuildfileCompilationException(msg: String, cause: Throwable = null, localizedMsg: String = null)
  extends ProjectConfigurationException(msg, cause, localizedMsg)

