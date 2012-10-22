package de.tototec.sbuild.eclipse

import de.tototec.sbuild.SBuildLogger
import de.tototec.sbuild.SBuildConsoleLogger
import de.tototec.sbuild.LogLevel
package object plugin {

  /** Print a debug message. */
  private[plugin] def debug(msg: => String, cause: Throwable = null) = {
    Console.err.println(msg)
    if(cause != null) {
      Console.err.println(cause.getMessage())
      cause.printStackTrace(Console.err)
    }
  }
}
