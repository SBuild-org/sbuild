package de.tototec.sbuild

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.net.URL
import java.util.zip.ZipInputStream
import scala.Array.canBuildFrom
import scala.util.matching.Regex
import java.text.DecimalFormat

object Util {

  var log: SBuildLogger = SBuildNoneLogger

  def delete(files: File*): Boolean = {
    var success = true;
    files.map(_ match {
      case f if f.isDirectory => {
        success = delete(f.listFiles: _*) && success
        log.log(LogLevel.Debug, "Deleting dir: " + f)
        success = f.delete && success
      }
      case f if f.exists => {
        log.log(LogLevel.Debug, "Deleting file: " + f)
        success = f.delete && success
      }
      case _ =>
    })
    success
  }

  def download(url: String, target: String, log: SBuildLogger = log): Option[Throwable] = {

    try {

      val targetFile = new File(target)

      targetFile.exists match {
        case true => { // File already exists
          log.log(LogLevel.Debug, "File '" + target + "' already downloaded")
          true
        }
        case false => { // File needs download

          val format = new DecimalFormat("#,##0.0")

          log.log(LogLevel.Info, s"Downloading ${url}")

          // copy into temp file

          val dir = targetFile.getAbsoluteFile.getParentFile
          dir.mkdirs
          val downloadTargetFile = File.createTempFile(".~" + targetFile.getName, "", dir)

          def cleanup =
            if (downloadTargetFile.exists)
              if (!downloadTargetFile.delete)
                downloadTargetFile.deleteOnExit

          val outStream = new BufferedOutputStream(new FileOutputStream(downloadTargetFile))
          try {
            val inStream = new BufferedInputStream(new URL(url).openStream)

            var last = System.currentTimeMillis
            var len = 0
            var break = false
            var alreadyLogged = false

            def logProgress = log.log(LogLevel.Info, s"Downloaded ${format.format(len / 1024)} kb")

            var buffer = new Array[Byte](1024)

            while (!break) {
              val now = System.currentTimeMillis
              if (len > 0 && now > last + 5000) {
                alreadyLogged = true
                logProgress
                last = now;
              }
              inStream.read(buffer, 0, 1024) match {
                case x if x < 0 => break = true
                case count => {
                  len = len + count
                  outStream.write(buffer, 0, count)
                }
              }
            }

            if (alreadyLogged && len > 0) {
              logProgress
            }

            inStream.close
          } catch {
            case e: FileNotFoundException =>
              outStream.close
              cleanup
              throw new SBuildException("Download resource does not exists: " + url, e);
            case e: IOException =>
              outStream.close
              cleanup
              throw new SBuildException("Error while downloading file: " + url, e);
          } finally {
            outStream.close
          }

          val renameSuccess = downloadTargetFile.renameTo(targetFile)
          if (!renameSuccess) {
            // move temp file to dest file
            val out = new FileOutputStream(targetFile)
            val in = new FileInputStream(downloadTargetFile)
            try {
              out.getChannel.transferFrom(in.getChannel, 0, Long.MaxValue)
            } finally {
              out.close
              in.close
            }
            cleanup
          }

        }
      }
      None
    } catch {
      case x: Throwable => Some(x)
    }

  }

  def recursiveListFilesAbsolute(dir: String, regex: Regex = ".*".r, log: SBuildLogger = SBuildNoneLogger): Array[String] = {
    recursiveListFiles(new File(dir), regex, log).map(_.getAbsolutePath)
  }

  //  def recursiveListFiles(dir: String, regex: Regex = ".*".r): Array[String] = {
  //    recursiveListFiles(new File(dir), regex).map(_.getPath)
  //  }

  def recursiveListFiles(dir: File, regex: Regex = ".*".r, log: SBuildLogger = SBuildNoneLogger): Array[File] = {
    dir.listFiles match {
      case allFiles: Array[File] =>
        allFiles.filter { f =>
          val include = f.isFile && regex.findFirstIn(f.getName).isDefined
          log.log(LogLevel.Debug, (if (include) "including " else "excluding ") + f)
          include
        } ++
          allFiles.filter(_.isDirectory).flatMap { d => recursiveListFiles(d, regex, log) }
      case null => Array()
    }
  }

  def unzip(archive: File, targetDir: File, selectedFiles: String*) {
    unzip(archive, targetDir, selectedFiles.map(f => (f, null)).toList)
  }

  def unzip(archive: File, targetDir: File, _selectedFiles: List[(String, File)]) {

    if (!archive.exists || !archive.isFile) throw new FileNotFoundException("Zip file cannot be found: " + archive);
    targetDir.mkdirs

    log.log(LogLevel.Debug, "Extracting zip archive '" + archive + "' to: " + targetDir)

    var selectedFiles = _selectedFiles
    val partial = !selectedFiles.isEmpty
    if (partial) log.log(LogLevel.Debug, "Only extracting some content of zip file")

    try {
      val zipIs = new ZipInputStream(new FileInputStream(archive))
      var zipEntry = zipIs.getNextEntry
      while (zipEntry != null && (!partial || !selectedFiles.isEmpty)) {
        val extractFile: Option[File] = if (partial) {
          if (!zipEntry.isDirectory) {
            val candidate = selectedFiles.find { case (name, _) => name == zipEntry.getName }
            if (candidate.isDefined) {
              selectedFiles = selectedFiles.filterNot(_ == candidate.get)
              if (candidate.get._2 != null) {
                Some(candidate.get._2)
              } else {
                val full = zipEntry.getName
                val index = full.lastIndexOf("/")
                val name = if (index < 0) full else full.substring(index)
                Some(new File(targetDir + "/" + name))
              }
            } else {
              None
            }
          } else {
            None
          }
        } else {
          if (zipEntry.isDirectory) {
            log.log(LogLevel.Debug, "  Creating " + zipEntry.getName);
            new File(targetDir + "/" + zipEntry.getName).mkdirs
            None
          } else {
            Some(new File(targetDir + "/" + zipEntry.getName))
          }
        }

        if (extractFile.isDefined) {
          log.log(LogLevel.Debug, "  Extracting " + zipEntry.getName);
          val targetFile = extractFile.get
          if (targetFile.exists
            && !targetFile.getParentFile.isDirectory) {
            throw new RuntimeException(
              "Expected directory is a file. Cannot extract zip content: "
                + zipEntry.getName);
          }
          // Ensure, that the directory exixts
          targetFile.getParentFile.mkdirs
          val outputStream = new BufferedOutputStream(new FileOutputStream(targetFile))
          copy(zipIs, outputStream);
          outputStream.close
          if (zipEntry.getTime > 0) {
            targetFile.setLastModified(zipEntry.getTime)
          }
        }

        zipEntry = zipIs.getNextEntry()
      }

      zipIs.close
    } catch {
      case e: IOException =>
        throw new RuntimeException("Could not unzip file: " + archive,
          e)
    }

    if (!selectedFiles.isEmpty) {
      throw new FileNotFoundException(s"""Could not found file "${selectedFiles.head._1}" in zip archive "${archive}".""")
    }

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

}