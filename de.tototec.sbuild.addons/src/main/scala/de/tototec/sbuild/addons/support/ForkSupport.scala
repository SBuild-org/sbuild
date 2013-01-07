package de.tototec.sbuild.addons.support

import java.io.InputStream
import java.io.OutputStream
import java.io.File
import de.tototec.sbuild.Project
import de.tototec.sbuild.Path
import de.tototec.sbuild.LogLevel

object ForkSupport {

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
    
    var result:Int = -1
    try {
    	result = p.waitFor
    } finally {
    	outThread.interrupt
    	p.getErrorStream.close
    	p.getInputStream.close
    }
    
    result
  }

  def copyInThread(in: InputStream, out: OutputStream) {
    new Thread("StreamCopyThread") {
      override def run {
        copy(in, out)
        out.flush()
      }
    }.start
  }

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

  def pathAsArg(pathes: Seq[File]): String = pathes.map(p => p.getAbsolutePath).mkString(File.pathSeparator)

  def whiteSpaceSeparated(seq: Seq[String]): String = seq.map(_.replaceAll(" ", "\\ ")).mkString(" ")

}