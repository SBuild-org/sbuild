package de.tototec.sbuild.eclipse

import de.tototec.sbuild.SBuildLogger
import de.tototec.sbuild.SBuildConsoleLogger
import de.tototec.sbuild.LogLevel
package object plugin {

  var log: SBuildLogger = new SBuildConsoleLogger(LogLevel.Info, LogLevel.Debug) {
    override def log(logLevel: LogLevel, msg: => String, cause: Throwable = null) {
      super.log(logLevel, msg, cause)
      Console.err.println(msg)
    }
  }

  /** Print a debug message. */
  private[plugin] def debug(msg: => String) = {
    log.log(LogLevel.Debug, msg)
  }
}