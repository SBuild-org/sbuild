package de.tototec.sbuild

import java.io.File
import scala.util.matching.Regex

object RichFile {

  implicit def toRichFile(file: File): RichFile = new RichFile(file)

  private[this] val log = Logger[RichFile.type]

  def deleteRecursive(files: File*): Boolean = {
    var success = true;
    files.map {
      case f if f.isDirectory => {
        success = deleteRecursive(f.listFiles: _*) && success
        log.debug("Deleting dir: " + f)
        success = f.delete && success
      }
      case f if f.exists => {
        log.debug("Deleting file: " + f)
        success = f.delete && success
      }
      case _ =>
    }
    success
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

  def deleteRecursive: Boolean = RichFile.deleteRecursive(file)

  def listFilesRecursive: Array[File] = RichFile.listFilesRecursive(file, ".*".r)
  def listFilesRecursive(fileNameRegex: Regex): Array[File] = RichFile.listFilesRecursive(file, fileNameRegex)

}

