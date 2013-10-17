package de.tototec.sbuild

trait Logger extends Serializable {
  def error(msg: => String, throwable: Throwable = null)
  def warn(msg: => String, throwable: Throwable = null)
  def info(msg: => String, throwable: Throwable = null)
  def debug(msg: => String, throwable: Throwable = null)
  def trace(msg: => String, throwable: Throwable = null)
}

object Logger {
  import scala.reflect.ClassTag
  import scala.reflect.classTag
  import scala.util.Success
  import scala.util.Try
  import java.lang.reflect.Method

  private[this] var cachedLoggerFactory: Option[String => Logger] = None
  def apply[T: ClassTag]: Logger = cachedLoggerFactory match {
    case Some(loggerFactory) => loggerFactory(classTag[T].runtimeClass.getName)
    case None =>
      // try to load SLF4J
      def delegatedLoadingOfLoggerFactory: String => Logger = {
        import org.slf4j.LoggerFactory
        LoggerFactory.getILoggerFactory()

        // if we are here, loading the LoggerFactory was successful
        loggedClass => new Logger {
          private[this] val underlying = LoggerFactory.getLogger(loggedClass)
          override def error(msg: => String, throwable: Throwable) = if (underlying.isErrorEnabled) underlying.error(msg, throwable)
          override def warn(msg: => String, throwable: Throwable) = if (underlying.isWarnEnabled) underlying.warn(msg, throwable)
          override def info(msg: => String, throwable: Throwable) = if (underlying.isInfoEnabled) underlying.info(msg, throwable)
          override def debug(msg: => String, throwable: Throwable) = if (underlying.isDebugEnabled) underlying.debug(msg, throwable)
          override def trace(msg: => String, throwable: Throwable) = if (underlying.isTraceEnabled) underlying.trace(msg, throwable)
        }
      }

      val loggerFactory = try {
        delegatedLoadingOfLoggerFactory
      } catch {
        case e: NoClassDefFoundError =>
          _: String => new Logger {
            override def error(msg: => String, throwable: Throwable) {}
            override def warn(msg: => String, throwable: Throwable) {}
            override def info(msg: => String, throwable: Throwable) {}
            override def debug(msg: => String, throwable: Throwable) {}
            override def trace(msg: => String, throwable: Throwable) {}
          }
      }

      cachedLoggerFactory = Some(loggerFactory)

      // retry, now initialized
      apply[T]
  }
}

trait SBuildLogger {
  def log(logLevel: LogLevel, msg: => String, cause: Throwable = null)
}

trait LogLevel
object LogLevel {
  case object Never extends LogLevel
  case object Info extends LogLevel
  case object Debug extends LogLevel
  case object Warn extends LogLevel
  case object Error extends LogLevel
  val all: Set[LogLevel] = Set(Error, Warn, Info, Debug)
  val debug: Set[LogLevel] = Set(Error, Warn, Info, Debug)
  val info: Set[LogLevel] = Set(Error, Warn, Info)
  val warn: Set[LogLevel] = Set(Error, Warn)
  val error: Set[LogLevel] = Set(Error)
}

object SBuildNoneLogger extends SBuildLogger {
  override def log(logLevel: LogLevel, msg: => String, cause: Throwable = null) {}
}

class SBuildConsoleLogger(enabledLogLevels: Set[LogLevel]) extends SBuildLogger {
  def this(enabledLogLevels: LogLevel*) = this(enabledLogLevels.toSet)
  override def log(logLevel: LogLevel, msg: => String, cause: Throwable = null) =
    if (enabledLogLevels.contains(logLevel)) {
      Console.println(msg)
      if (cause != null) {
        cause.printStackTrace(Console.out)
      }
    }
}
