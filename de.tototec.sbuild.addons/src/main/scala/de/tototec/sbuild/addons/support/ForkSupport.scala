package de.tototec.sbuild.addons.support

import java.io.InputStream
import java.io.OutputStream
import java.io.File
import de.tototec.sbuild.Project
import de.tototec.sbuild.Path
import de.tototec.sbuild.LogLevel

/**
 * Provided various support functions for forking of processes.
 */
object ForkSupport {

  /**
   * Run the system default JVM with the given classpath and arguments.
   *
   * @param classpath The classpath used by the JVM.
   * @param arguments The arguments to the JVM. The first parameter should be the Java class containing a `main` method.
   * @param interactive If `true`, the input stream is routed to the newly started process, to read user input.
   *
   *  @return The return value of the Java process. Typically 0 indicated success whereas any other value is treated as error.
   */
  def runJavaAndWait(classpath: Seq[File], arguments: Array[String], interactive: Boolean = false)(implicit project: Project): Int = {

    val java = "java"

    val cpArgs = classpath match {
      case null | Seq() => Array[String]()
      case cp => Array("-cp", pathAsArg(classpath))
    }

    runAndWait(
      command = Array(java) ++ cpArgs ++ arguments,
      interactive = interactive)
  }

  /**
   * Run a command.
   * 
   * @param command The command and its arguments.
   * @param interactive If `true`, the input stream is routed to the newly started process, to read user input.
   *
   *  @return The return value of the Java process. Typically 0 indicated success whereas any other value is treated as error.
   */
  def runAndWait(command: Array[String], interactive: Boolean = false)(implicit project: Project): Int = {
    val pb = new ProcessBuilder(command: _*)
    project.log.log(LogLevel.Debug, "Run command: " + command.mkString(" "))
    pb.directory(Path("."))
    val p = pb.start

    ForkSupport.copyInThread(p.getErrorStream, Console.err)
    ForkSupport.copyInThread(p.getInputStream, Console.out)

    val in = System.in
    val out = p.getOutputStream

    val outThread = new Thread() {
      override def run {
        try {
          while (true) {
            if (in.available > 0) {
              in.read match {
                case -1 =>
                case read =>
                  out.write(read)
                  out.flush
              }
            } else {
              Thread.sleep(50)
            }
          }
        } catch {
          case e: InterruptedException => // this is ok
        }
      }
    }
    outThread.start()

    var result: Int = -1
    try {
      result = p.waitFor
    } finally {
      outThread.interrupt
      p.getErrorStream.close
      p.getInputStream.close
    }

    result
  }

  /**
   * Starts a new thread which copies an InputStream into an Output stream. Does not close the streams.
   */
  def copyInThread(in: InputStream, out: OutputStream) {
    new Thread("StreamCopyThread") {
      override def run {
        copy(in, out)
        out.flush()
      }
    }.start
  }

  /**
   * Copies an InputStream into an OutputStream. Does not close the streams.
   */
  def copy(in: InputStream, out: OutputStream) {
    val buf = new Array[Byte](1024)
    var len = 0
    while ({
      len = in.read(buf)
      len > 0
    }) {
      out.write(buf, 0, len)
    }
  }

  /**
   * Converts a Seq of files into a string containing the absolute file pathes concatenated with the platform specific path separator (":" on Unix, ";" on Windows).
   */
  def pathAsArg(pathes: Seq[File]): String = pathes.map(p => p.getAbsolutePath).mkString(File.pathSeparator)

  /**
   * Concatetes the input string into a single white space separated string. Any whitespace in the input strings will be masked with a backslash ("\").
   */
  def whiteSpaceSeparated(seq: Seq[String]): String = seq.map(_.replaceAll(" ", "\\ ")).mkString(" ")

}