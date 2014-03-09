package org.sbuild

import java.io.File
import scala.util.matching.Regex
import java.io.IOException
import java.io.FileInputStream
import java.io.FileOutputStream

object RichFile {

  implicit def toRichFile(file: File): RichFile = new RichFile(file)

  private[this] val log = Logger[RichFile.type]

  def deleteFile(file: File): Unit = {
    log.debug(s"Deleting ${if (file.isDirectory) "dir" else "file"}: ${file}")
    file.delete match {
      case false if file.exists =>
        throw new RuntimeException(s"Could not delete ${if (file.isDirectory) "dir" else "file"}: ${file}")
      case _ =>
    }
  }

  def deleteFiles(files: File*): Unit = files.map(f => (f.delete, f)).filter {
    case (success, file) => !success || file.exists
  } match {
    case Seq() =>
    case x => throw new RuntimeException("Could not delete files: " + x.mkString(", "))
  }

  def deleteRecursive(files: File*): Unit = files.map { f =>
    if (f.isDirectory) deleteRecursive(f.listFiles: _*)
    deleteFile(f)
  }

  def listFilesRecursive(dir: File, fileNameRegex: Regex = ".*".r): Array[File] = {
    dir.listFiles match {
      case allFiles: Array[File] =>
        allFiles.filter { f =>
          val include = f.isFile && fileNameRegex.findFirstIn(f.getName).isDefined
          log.debug((if (include) "including " else "excluding ") + f)
          include
        } ++
          allFiles.filter(_.isDirectory).flatMap { d => listFilesRecursive(d, fileNameRegex) }
      case null => Array()
    }
  }

  def copy(source: File, dest: File, preserveDate: Boolean): Unit = {
    if (!source.exists()) throw new IOException("Cannot copy non-existing file: " + source)

    if (source.isDirectory()) {
      if (dest.exists() && !dest.isDirectory()) {
        throw new IOException("Cannot copy directory " + source + ". Target exists and is not a directory: " + dest)
      }
      dest.mkdirs()
      source.listFiles().foreach { file =>
        copy(file, dest / file.getName(), preserveDate)
      }
    } else {
      val realDest = if (dest.exists() && dest.isDirectory()) {
        dest / source.getName()
      } else {
        dest.getParentFile() match {
          case null => // nothing to do
          case parent => parent.mkdirs()
        }
        dest
      }

      val is = new FileInputStream(source)
      val os = new FileOutputStream(realDest)
      try {
        val input = is.getChannel()
        val output = os.getChannel()
        val size = input.size
        val bufferSize: Long = 1024 * 1024 * 10
        var pos = 0L
        while (pos < size) {
          val count = scala.math.min(bufferSize, size - pos)
          pos += output.transferFrom(input, pos, count)
        }
      } finally {
        os.close()
        is.close()
      }

      if (preserveDate) realDest.setLastModified(source.lastModified())

    }
  }

}

class RichFile(val file: File) extends AnyVal {

  def deleteFile: Unit = RichFile.deleteFile(file)
  def deleteRecursive: Unit = RichFile.deleteRecursive(file)

  def listFilesRecursive: Array[File] = RichFile.listFilesRecursive(file, ".*".r)
  def listFilesRecursive(fileNameRegex: Regex): Array[File] = RichFile.listFilesRecursive(file, fileNameRegex)

  def pathRelativeTo(baseDir: File): Option[String] = (baseDir, file) match {
    case (b, f) if b.isAbsolute && f.isAbsolute => Some(baseDir.toURI.relativize(f.toURI).getPath)
    case _ => None
  }

  def /(name: String): File = new File(file, name)

  def copyTo(dest: File): Unit = RichFile.copy(file, dest, preserveDate = false)

}

