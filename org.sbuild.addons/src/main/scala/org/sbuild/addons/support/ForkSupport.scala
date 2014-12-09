package org.sbuild.addons.support

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import org.sbuild.Logger
import org.sbuild.Path
import org.sbuild.Project

/**
 * Provided various support functions for forking of processes.
 */
object ForkSupport {

  private[this] val log = Logger[ForkSupport.type]

  /**
   * Run the system default JVM with the given classpath and arguments.
   *
   * @param classpath The classpath used by the JVM.
   * @param arguments The arguments to the JVM. The first parameter should be the Java class containing a `main` method.
   * @param interactive If `true`, the input stream is routed to the newly started process, to read user input.
   * @param errorsIntoOutput If `true`, the error output stream of the started process is routed to its output stream.
   * @param directory The working directory of the started process. Relative paths are relative to the project directory.
   * @param failOnError If `true` the method will throw an [[RuntimeException]] if the return code is not `0`.
   *
   * @return The return value of the Java process. Typically 0 indicated success whereas any other value is treated as error.
   */
  def runJavaAndWait(classpath: Seq[File],
                     arguments: Array[String],
                     interactive: Boolean = false,
                     errorsIntoOutput: Boolean = true,
                     failOnError: Boolean = true,
                     directory: File = new File("."))(implicit project: Project): Int = {

    log.debug("About to run Java process")

    // TODO: lookup java by JAVA_HOME env variable
    val javaHome = System.getenv("JAVA_HOME")
    val java =
      if (javaHome != null) s"${javaHome}/bin/java"
      else "java"
    log.debug("Using java executable: " + java)

    val cpArgs = classpath match {
      case null | Seq() => Array[String]()
      case cp => Array("-cp", pathAsArg(classpath))
    }
    log.debug("Using classpath args: " + cpArgs.mkString(" "))

    runAndWait(
      command = Array(java) ++ cpArgs ++ arguments,
      interactive = interactive,
      errorsIntoOutput = errorsIntoOutput,
      failOnError = failOnError
    )
  }

  /**
   * Run a command.
   *
   * @param command The command and its arguments.
   * @param interactive If `true`, the input stream is routed to the newly started process, to read user input.
   * @param errorsIntoOutput If `true`, the error output stream of the started process is routed to its output stream.
   * @param directory The working directory of the started process. Relative paths are relative to the project directory.
   * @param failOnError If `true` the method will throw an [[RuntimeException]] if the return code is not `0`.
   *
   * @return The return value of the Java process. Typically 0 indicated success whereas any other value is treated as error.
   */
  def runAndWait(command: Array[String],
                 interactive: Boolean = false,
                 errorsIntoOutput: Boolean = true,
                 directory: File = new File("."),
                 failOnError: Boolean = true,
                 env: Map[String, String] = Map())(implicit project: Project): Int = {
    val pb = new ProcessBuilder(command: _*)
    log.debug("Run command: " + command.mkString(" "))
    // if directory is not absolute, Path will make it so, relative to the project directory.
    pb.directory(Path(directory))
    if (!env.isEmpty) env.foreach { case (k, v) => pb.environment().put(k, v) }
    val p = pb.start

    //    val shutdownHook = new Thread("Kill external process shutdown hook") {
    //      override def run() {
    //        log.info("About to destroy process: " + p)
    //        p.destroy()
    //      }
    //    }
    //    log.debug("Adding shutdown hook for process: " + p)
    //    Runtime.getRuntime().addShutdownHook(shutdownHook)

    val errThread = ForkSupport.asyncCopy(p.getErrorStream, if (errorsIntoOutput) Console.out else Console.err)
    val inThread = ForkSupport.asyncCopy(p.getInputStream, Console.out, interactive)

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
      //      log.debug("Removing shutdown hook")
      //      Runtime.getRuntime().removeShutdownHook(shutdownHook)
    } finally {
      outThread.interrupt()
      try {
        errThread.join()
      } finally {
        p.getErrorStream.close
      }
      try {
        inThread.join()
      } finally {
        p.getInputStream.close
      }
    }

    if (failOnError && result != 0) throw new RuntimeException("Execution of command \"" + command.headOption.getOrElse("") + "\" failed with exit code " + result)
    result
  }

  /**
   * Starts a new thread which copies an InputStream into an Output stream. Does not close the streams.
   */
  def copyInThread(in: InputStream, out: OutputStream): Thread = asyncCopy(in, out, false)

  /**
   * Starts a new thread which copies an InputStream into an Output stream. Does not close the streams.
   */

  def asyncCopy(in: InputStream, out: OutputStream, immediately: Boolean = false): Thread =
    new Thread("StreamCopyThread") {
      override def run {
        copy(in, out, immediately)
        out.flush()
      }
      start
    }

  /**
   * Copies an InputStream into an OutputStream. Does not close the streams.
   */
  def copy(in: InputStream, out: OutputStream, immediately: Boolean = false): Unit = {
    if (immediately) {
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
    } else {
      val buf = new Array[Byte](1024)
      var len = 0
      while ({
        len = in.read(buf)
        len > 0
      }) {
        out.write(buf, 0, len)
      }
    }
  }

  /**
   * Converts a Seq of files into a string containing the absolute file paths concatenated with the platform specific path separator (":" on Unix, ";" on Windows).
   */
  def pathAsArg(paths: Seq[File]): String = paths.map(p => p.getPath).mkString(File.pathSeparator)

  /**
   * Concatetes the input string into a single white space separated string. Any whitespace in the input strings will be masked with a backslash ("\").
   */
  def whiteSpaceSeparated(seq: Seq[String]): String = seq.map(_.replaceAll(" ", "\\ ")).mkString(" ")

}