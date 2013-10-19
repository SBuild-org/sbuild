package de.tototec.sbuild

import java.io.OutputStream
import java.io.PrintStream

trait CmdlineMonitor {
  import CmdlineMonitor._

  def mode: OutputMode

  /** Show a error message to the user. */
  def error(msg: => String) { error(Always, msg) }
  def error(when: OutputMode, msg: => String) { info(when, "Error: " + msg) }

  /** Show a warning to the user. */
  def warn(msg: => String) { warn(Always, msg) }
  def warn(when: OutputMode, msg: => String) { info(when, "Warning: " + msg) }

  /* Show a information to the user. */
  def info(msg: => String) { info(Always, msg) }
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

class OutputStreamCmdlineMonitor(outputStream: OutputStream, override val mode: CmdlineMonitor.OutputMode)
    extends CmdlineMonitor {
  private[this] val printStream = outputStream match {
    case ps: PrintStream => ps
    case _ => new PrintStream(outputStream)
  }
  override def info(when: CmdlineMonitor.OutputMode, msg: => String) {
    if (mode.contains(when)) printStream.println(msg)
  }
  override def showStackTrace(when: CmdlineMonitor.OutputMode, throwable: Throwable) {
    if (mode.contains(when)) throwable.printStackTrace(printStream)
  }
}

object NoopCmdlineMonitor extends CmdlineMonitor {
  override def mode: CmdlineMonitor.OutputMode = CmdlineMonitor.Always
  override def info(when: CmdlineMonitor.OutputMode, msg: => String) {}
  override def showStackTrace(when: CmdlineMonitor.OutputMode, throwable: Throwable) {}
}