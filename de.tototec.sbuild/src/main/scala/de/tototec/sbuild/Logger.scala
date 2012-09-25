package de.tototec.sbuild

trait SBuildLogger {
  def log(LogLevel: LogLevel, msg: => String, cause: Throwable = null)
}

trait LogLevel
object LogLevel {
  case object Info extends LogLevel
  case object Debug extends LogLevel
  val all: Set[LogLevel] = Set(Info, Debug)
}

object SBuildNoneLogger extends SBuildLogger {
  override def log(LogLevel: LogLevel, msg: => String, cause: Throwable = null) {}
}

class SBuildConsoleLogger(_enabledLogLevels: LogLevel*) extends SBuildLogger {
  val enabledLogLevels = _enabledLogLevels.toSet
  override def log(logLevel: LogLevel, msg: => String, cause: Throwable = null) =
    if (enabledLogLevels.contains(logLevel)) {
      Console.println(msg)
      if (cause != null) {
        cause.printStackTrace(Console.out)
      }
    }
}
