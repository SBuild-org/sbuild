package de.tototec.sbuild.scriptcompiler

import scala.tools.nsc.Driver
import scala.tools.nsc.Global
import scala.tools.nsc.interactive
import scala.tools.nsc.Settings
import scala.tools.nsc.reporters.ConsoleReporter
import scala.tools.nsc.CompilerCommand
import scala.reflect.internal.FatalError
import java.io.BufferedReader
import java.io.PrintWriter

class RecordingConsoleReporter(settings: Settings, reader: BufferedReader, writer: PrintWriter)
    extends ConsoleReporter(settings, reader, writer) {

  def this(settings: Settings) = this(settings, Console.in, new PrintWriter(Console.err, true))

  private[this] var _recordedOutput: List[String] = Nil

  /** Prints and records the message. */
  override def printMessage(msg: String) {
    _recordedOutput ::= msg
    super.printMessage(msg);
  }

  def getRecordedOutput: Seq[String] = _recordedOutput.reverse
  def clearRecordedOutput { _recordedOutput = Nil }

}

class ScriptCompiler extends Driver {

  def getRecordedOutput: Seq[String] = reporter match {
    case r: RecordingConsoleReporter =>
      r.getRecordedOutput
    case _ => Nil
  }

  def clearRecordedOutput: Unit = reporter match {
    case r: RecordingConsoleReporter =>
      r.clearRecordedOutput
    case _ => Nil
  }

  override def newCompiler(): Global =
    if (settings.Yrangepos.value) new Global(settings, reporter) with interactive.RangePositions
    else Global(settings, reporter)

  override def process(args: Array[String]) {
    val ss = new Settings(scalacError)
    reporter = new RecordingConsoleReporter(ss)
    command = new CompilerCommand(args.toList, ss)
    settings = command.settings

    if (settings.version.value) {
      reporter.echo(versionMsg)
    } else if (processSettingsHook()) {
      val compiler = newCompiler()
      try {
        if (reporter.hasErrors)
          reporter.flush()
        else if (command.shouldStopWithInfo)
          reporter.echo(command.getInfoMessage(compiler))
        else
          doCompile(compiler)
      } catch {
        case ex: Throwable =>
          compiler.reportThrowable(ex)
          ex match {
            case FatalError(msg) => // signals that we should fail compilation.
            case _ => throw ex // unexpected error, tell the outside world.
          }
      }
    }
  }

}

