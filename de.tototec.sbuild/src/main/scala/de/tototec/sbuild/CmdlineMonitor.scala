package de.tototec.sbuild

import java.io.OutputStream
import java.io.PrintStream

trait CmdlineMonitor {
  import CmdlineMonitor._

  def mode: OutputMode

  /** Show a error message to the user. */
  def error(msg: => String)
  def error(when: OutputMode, msg: => String)

  /** Show a warning to the user. */
  def warn(msg: => String)
  def warn(when: OutputMode, msg: => String)

  /* Show a information to the user. */
  def info(msg: => String)
  def info(when: OutputMode, msg: => String)

  def showStackTrace(when: OutputMode, throwable: Throwable)
}

object CmdlineMonitor {
  sealed abstract class OutputMode(protected val level: Byte) {
    def contains(mode: OutputMode): Boolean = level >= mode.level
  }
  case object Always extends OutputMode(0)
  case object Quiet extends OutputMode(1)
  case object Default extends OutputMode(2)
  case object Verbose extends OutputMode(3)
}

class OutputStreamCmdlineMonitor(outputStream: OutputStream,
                                 override val mode: CmdlineMonitor.OutputMode,
                                 messagePrefix: String = "",
                                 errorPrefix: String = "Error: ",
                                 warnPrefix: String = "Warn: ")
    extends CmdlineMonitor {
  import CmdlineMonitor._

  private[this] val log = Logger[OutputStreamCmdlineMonitor]

  private[this] val printStream = outputStream match {
    case ps: PrintStream => ps
    case _ => new PrintStream(outputStream)
  }

  override def error(msg: => String) { error(Always, msg) }
  override def error(when: OutputMode, msg: => String) { println(when, errorPrefix + messagePrefix + msg) }

  override def warn(msg: => String) { warn(Always, msg) }
  override def warn(when: OutputMode, msg: => String) { println(when, warnPrefix + messagePrefix + msg) }

  override def info(msg: => String) { info(Always, msg) }
  override def info(when: CmdlineMonitor.OutputMode, msg: => String) { println(when, messagePrefix + msg) }

  protected def println(when: CmdlineMonitor.OutputMode, msg: => String) {
    if (mode.contains(when)) {
      printStream.println(msg)
      log.debug("outputting (" + when + ") " + msg)
    } else {
      log.debug("swallowing (" + when + ") " + msg)
    }
  }

  override def showStackTrace(when: CmdlineMonitor.OutputMode, throwable: Throwable) {
    if (mode.contains(when)) throwable.printStackTrace(printStream)
  }
}

object NoopCmdlineMonitor extends CmdlineMonitor {
  import CmdlineMonitor._

  private[this] val log = Logger[NoopCmdlineMonitor.type]

  override def mode: CmdlineMonitor.OutputMode = CmdlineMonitor.Always

  override def error(msg: => String) { log.debug("swallowing error: " + msg) }
  override def error(when: OutputMode, msg: => String) { log.debug("swallowing error: (" + when + ") " + msg) }

  override def warn(msg: => String) { log.debug("swallowing warn: " + msg) }
  override def warn(when: OutputMode, msg: => String) { log.debug("swallowing warn: (" + when + ") " + msg) }

  override def info(msg: => String) { log.debug("swallowing info: " + msg) }
  override def info(when: CmdlineMonitor.OutputMode, msg: => String) { log.debug("swallowing info: (" + when + ") " + msg) }

  override def showStackTrace(when: CmdlineMonitor.OutputMode, throwable: Throwable) {}
}