package de.tototec.sbuild

import java.io.File

trait FileSupport { this: File =>
  def deleteRecursive: Boolean = FileSupport.delete(this)
  def deleteRec: Boolean = deleteRecursive
  //  def formatRelativeTo(file: File): String
}

object FileSupport {
  private[this] val log = Logger[FileSupport.type]

  def delete(files: File*): Boolean = {
    var success = true;
    files.map {
      case f if f.isDirectory => {
        success = delete(f.listFiles: _*) && success
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

}