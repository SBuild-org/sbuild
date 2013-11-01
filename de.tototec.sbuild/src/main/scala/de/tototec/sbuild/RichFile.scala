package de.tototec.sbuild

import java.io.File
import scala.util.matching.Regex

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

}

class RichFile(val file: File) {

  def deleteFile: Unit = RichFile.deleteFile(file)
  def deleteRecursive: Unit = RichFile.deleteRecursive(file)

  def listFilesRecursive: Array[File] = RichFile.listFilesRecursive(file, ".*".r)
  def listFilesRecursive(fileNameRegex: Regex): Array[File] = RichFile.listFilesRecursive(file, fileNameRegex)

}

