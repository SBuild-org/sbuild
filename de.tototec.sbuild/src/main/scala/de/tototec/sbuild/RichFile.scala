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

  def recursiveFiles(dir: File, fileRegex: Regex = ".*".r): Array[File] = {
    dir.listFiles match {
      case allFiles: Array[File] =>
        allFiles.filter { f =>
          val include = f.isFile && fileRegex.findFirstIn(f.getName).isDefined
          log.debug((if (include) "including " else "excluding ") + f)
          include
        } ++
          allFiles.filter(_.isDirectory).flatMap { d => recursiveFiles(d, fileRegex) }
      case null => Array()
    }
  }

}

class RichFile(file: File) {

  def deleteRecursive: Boolean = RichFile.deleteRecursive(file)

  def recursiveFiles: Array[File] = RichFile.recursiveFiles(file, ".*".r)

  def recursiveFiles(fileRegex: Regex): Array[File] = RichFile.recursiveFiles(file, fileRegex)

}

