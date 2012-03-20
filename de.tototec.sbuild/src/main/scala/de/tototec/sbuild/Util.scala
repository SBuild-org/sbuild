package de.tototec.sbuild

import java.io.FileNotFoundException
import scala.util.matching.Regex
import java.io.RandomAccessFile
import java.io.IOException
import java.net.URL
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import de.tototec.sbuild.runner.SBuild
import java.io.{ File => JFile }
import scala.tools.nsc.io.File
import scala.tools.nsc.io.Directory
import java.util.zip.ZipFile
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
object Util {

  def verbose(msg: => String) = {
    SBuild.verbose(msg)
  }

  def delete(files: JFile*) {
    files.map(_ match {
      case f if f.isDirectory => {
        delete(f.listFiles: _*)
        verbose("Deleting dir: " + f)
        f.delete
      }
      case f if f.exists => {
        verbose("Deleting file: " + f)
        f.delete
      }
      case _ =>
    })
  }

  def download(url: String, target: String): Option[Throwable] = {

    try {

      val targetFile = new JFile(target)

      targetFile.exists match {
        case true => { // File already exists
          verbose("File '" + target + "' already downloaded")
          true
        }
        case false => { // File needs download

          def downloadIntoBuffer(url: String): Array[Byte] = {

            val buff = new ByteArrayOutputStream
            try {
              verbose("Downloading '" + url + "'")
              val fileUrl = new URL(url)
              val in = new BufferedInputStream(fileUrl.openStream())
              var last = System.currentTimeMillis
              var len = 0
              var break = false
              while (!break) {
                val now = System.currentTimeMillis();
                if (now > last + 1000) {
                  verbose("Downloaded " + len + " bytes");
                  last = now;
                }
                in.read() match {
                  case x if x < 0 => break = true
                  case x => {
                    len = len + 1
                    buff.write(x)
                  }
                }
              }
              in.close
            } catch {
              case e: FileNotFoundException => throw new SBuildException("Download resource does not exists: " + url, e);
              case e: IOException => throw new SBuildException("Error while downloading file: " + url, e);
            }
            buff.toByteArray
          }

          def writeDataToFile(data: Array[Byte], targetFile: JFile): Boolean = {
            targetFile.getParentFile match {
              case dir: JFile => dir.mkdirs
              case _ =>
            }
            try {
              val outFile = new RandomAccessFile(targetFile, "rw");
              outFile.setLength(data.length)
              outFile.write(data)
              outFile.close
              true
            } catch {
              case e: IOException => throw new SBuildException("Error saving file: " + target, e)
            }
          }

          writeDataToFile(downloadIntoBuffer(url), targetFile)
        }
      }

      None
    } catch {
      case x: Throwable => Some(x)
    }

  }

  def recursiveListFiles(dir: String, regex: Regex = ".*".r): Array[String] = {
    recursiveListFiles(new JFile(dir), regex).map(_.getPath)
  }

  def recursiveListFilesAbsolute(dir: String, regex: Regex = ".*".r): Array[String] = {
    recursiveListFiles(new JFile(dir), regex).map(_.getAbsolutePath)
  }

  def recursiveListFiles(dir: JFile, regex: Regex): Array[JFile] = {
    dir.listFiles match {
      case allFiles: Array[JFile] =>
        allFiles.filter(f => f.isFile && regex.findFirstIn(f.getName).isDefined) ++
          allFiles.filter(_.isDirectory).flatMap(recursiveListFiles(_, regex))
      case null => Array()
    }
  }

  def unzip(archive: File, targetDir: Directory, selectedFiles: String*) {
    unzip(archive, targetDir, selectedFiles.map(f => (f, null)).toList)
  }

  def unzip(archive: File, targetDir: Directory, _selectedFiles: List[(String, File)]) {

    if (!archive.exists) throw new RuntimeException("Zip file cannot be found: " + archive);
    targetDir.createDirectory(true, false)

    verbose("Extracting zip archive '" + archive + "' to: " + targetDir)

    var selectedFiles = _selectedFiles
    val partial = !selectedFiles.isEmpty
    if (partial) verbose("Only extracting some content of zip file")

    try {
      val zip = new ZipFile(archive.jfile)
      val entries = zip.entries
      while (entries.hasMoreElements && (!partial || !selectedFiles.isEmpty)) {
        val zipEntry = entries.nextElement

        val extractFile: Option[JFile] = if (partial) {
          if (!zipEntry.isDirectory) {
            val candidate = selectedFiles.find(p => p._1 == zipEntry.getName)
            if (candidate.isDefined) {
            	selectedFiles -= candidate.get
              if (candidate.get._2 != null) {
                Some(candidate.get._2.jfile)
              } else {
                val full = zipEntry.getName
                val index = full.lastIndexOf("/")
                val name = if (index < 0) full else full.substring(index)
                Some(new JFile(targetDir + "/" + name))
              }
            } else {
              None
            }
          } else {
            None
          }
        } else {
          if (zipEntry.isDirectory) {
            verbose("  Creating " + zipEntry.getName);
            new JFile(targetDir + "/" + zipEntry.getName).mkdirs
            None
          } else {
            Some(new JFile(targetDir + "/" + zipEntry.getName))
          }
        }

        if (extractFile.isDefined) {
          verbose("  Extracting " + zipEntry.getName);
          val targetFile = extractFile.get
          if (targetFile.exists
            && !targetFile.getParentFile.isDirectory) {
            throw new RuntimeException(
              "Expected directory is a file. Cannot extract zip content: "
                + zipEntry.getName());
          }
          // Ensure, that the directory exixts
          targetFile.getParentFile.mkdirs
          val outputStream = new BufferedOutputStream(new FileOutputStream(targetFile))
          val inputStream = zip.getInputStream(zipEntry)
          copy(inputStream, outputStream);
          outputStream.close
          inputStream.close
          if (zipEntry.getTime() > 0) {
            targetFile.setLastModified(zipEntry.getTime)
          }
        }
      }
    } catch {
      case e: IOException =>
        throw new RuntimeException("Could not unzip file: " + archive,
          e)
    }

  }

  private def copy(in: InputStream, out: OutputStream) {
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