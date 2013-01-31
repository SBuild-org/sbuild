package de.tototec.sbuild

trait SBuildLogger {
  def log(LogLevel: LogLevel, msg: => String, cause: Throwable = null)
}

trait LogLevel
object LogLevel {
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
  override def log(LogLevel: LogLevel, msg: => String, cause: Throwable = null) {}
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
