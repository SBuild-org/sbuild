package de.tototec.sbuild.internal

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Proxy
import java.net.URL
import java.text.DecimalFormat
import java.util.zip.ZipInputStream

import scala.Array.canBuildFrom
import scala.util.matching.Regex

import de.tototec.sbuild.CmdlineMonitor
import de.tototec.sbuild.Logger
import de.tototec.sbuild.NoopCmdlineMonitor
import de.tototec.sbuild.RichFile
import de.tototec.sbuild.SBuildException

object Util extends Util

class Util {

  private[this] val log = Logger[Util.type]
  private[sbuild] var monitor: CmdlineMonitor = NoopCmdlineMonitor

  @deprecated("Use RichFile.deleteRecursive instead.", "0.6.0.9002")
  def delete(files: File*): Boolean = delete(None, files: _*)

  @deprecated("Use RichFile.deleteRecursive instead.", "0.6.0.9002")
  def delete(onDelete: Option[File => Unit], files: File*): Boolean = {
    var success = true;
    files.map {
      case f if f.isDirectory => {
        success = delete(f.listFiles: _*) && success
        log.debug("Deleting dir: " + f)
        onDelete.map(_(f))
        success = f.delete && success
      }
      case f if f.exists => {
        log.debug("Deleting file: " + f)
        onDelete.map(_(f))
        success = f.delete && success
      }
      case _ =>
    }
    success
  }

  def download(url: String,
               target: String,
               monitor: CmdlineMonitor = monitor,
               userAgent: Option[String],
               proxy: Proxy = Proxy.NO_PROXY): Option[Throwable] = {

    val retryCount = 5

    try {

      val targetFile = new File(target)

      targetFile.exists match {
        case true => { // File already exists
          monitor.info(CmdlineMonitor.Verbose, "File '" + target + "' already downloaded")
          true
        }
        case false => { // File needs download

          monitor.info(CmdlineMonitor.Default, s"Downloading ${url}")

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

            var lastContentLength: Option[Long] = None
            var len = 0
            var lastRetryLen = 0
            var retry = true
            var retries = 0

            while (retry) {
              retry = false

              val connection = new URL(url).openConnection(proxy)
              userAgent.map { agent => connection.setRequestProperty("User-Agent", agent) }
              if (len > 0) {
                // TODO: also check http header Accept-Ranges
                connection.setRequestProperty("Range", s"bytes=${len}-")
              }
              val inStream = new BufferedInputStream(connection.getInputStream())

              // TODO: evaluate status code, e.g. 404

              // connection opened
              val contentLength = lastContentLength.getOrElse {
                val cl = connection.getHeaderField("content-length") match {
                  case null => -1
                  case length => try { length.toLong } catch { case _: Exception => -1 }
                }
                lastContentLength = Some(cl)
                cl
              }

              var last = System.currentTimeMillis
              var break = false
              var alreadyLogged = false
              val forceLogAfter = 5000
              val bufferSize = 1024

              val format = new DecimalFormat("#,##0.#")
              def formatLength(length: Long): String = format.format(length / 1024)

              def logProgress = if (contentLength > 0) {
                monitor.info(CmdlineMonitor.Default, s"Downloaded ${formatLength(len)} of ${formatLength(contentLength)} kb (${format.format((len.toDouble * 1000 / contentLength.toDouble).toLong.toDouble / 10)}%) from ${url}")
              } else {
                monitor.info(CmdlineMonitor.Default, s"Downloaded ${formatLength(len)} kb from ${url}")
              }

              var buffer = new Array[Byte](bufferSize)

              while (!break) {
                val now = System.currentTimeMillis
                if (len > 0 && now > last + forceLogAfter) {
                  alreadyLogged = true
                  logProgress
                  last = now;
                }
                inStream.read(buffer, 0, bufferSize) match {
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

              // TODO: if no resume is supported, retry n times before giving up
              // TODO: implement a timeout, to avoid ever-blocking downloads

              contentLength match {
                case l if l < 0 => // cannot use contentLength to verify result
                case l if len == l => // download size is equal to expected size => good
                case l if len < l =>
                  // stream closed to early
                  monitor.info(CmdlineMonitor.Default, s"Download stream closed before download was complete from ${url}")

                  if (retries < retryCount) {
                    monitor.info(CmdlineMonitor.Default, s"Resuming download from ${url}")
                    retry = true
                    alreadyLogged = true
                    retries = if (len > lastRetryLen) 0 else (retries + 1)
                    lastRetryLen = len
                  } else {
                    outStream.close
                    cleanup
                    throw new SBuildException(s"To many failed retries (s${retries}). Cannot download from ${url}");
                  }
                case _ =>
                  outStream.close
                  cleanup
                  throw new SBuildException(s"Size of downloaded file does not match expected size: ${url}");
              }

              inStream.close

            }

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

  def recursiveListFilesAbsolute(dir: String, regex: Regex = ".*".r): Array[String] = {
    RichFile.listFilesRecursive(new File(dir), regex).map(_.getAbsolutePath)
  }

  //  def recursiveListFiles(dir: String, regex: Regex = ".*".r): Array[String] = {
  //    recursiveListFiles(new File(dir), regex).map(_.getPath)
  //  }

  @deprecated("Use RichFile.listFilesRecursive instead", "0.6.0.9002")
  def recursiveListFiles(dir: File, regex: Regex = ".*".r): Array[File] = RichFile.listFilesRecursive(dir, regex)

  def unzip(archive: File, targetDir: File, selectedFiles: String*) {
    unzip(archive, targetDir, selectedFiles.map(f => (f, null)).toList, monitor, None)
  }

  def unzip(archive: File, targetDir: File, _selectedFiles: List[(String, File)], monitor: CmdlineMonitor) {
    unzip(archive, targetDir, _selectedFiles, monitor, None)
  }

  /**
   * Extract files from a ZIP archive.
   * If the list of `selectedFiles` is empty and no `fileSelector` was given, than all files will be extracted.
   *
   * @param archive The file denoting a ZIP archive.
   * @param targetDir The base directory, where the extracted files will be stored.
   * @param selectedFiles A list of name-file pairs denoting which archive content should be extracted into which file.
   *   The name if the path inside the archive.
   *   The file will be the place that file will be extracted to.
   *   If the file value is `null`, that the file will be extracted into the `targetDir` without any sub directory created.
   *   @param monitor A `[CmdlineMonitor]` to report this functions progress and messages.
   * @param fileSelector A filter used to decide if a file in the archive should be extracted or not.
   *   `fileSelector` is not able to exclude files already selected with `selectedFiles`.
   *   If a selector is given (`[scala.Some]`), that only those files will be extracted, for which the selector returns `true`.
   *
   * @return A `Seq` of all extracted files.
   *
   * @since 0.7.1.9000
   */
  def unzip(archive: File, targetDir: File, selectedFiles: List[(String, File)], monitor: CmdlineMonitor, fileSelector: Option[String => Boolean]): Seq[File] = {

    if (!archive.exists || !archive.isFile) throw new FileNotFoundException("Zip file cannot be found: " + archive);
    targetDir.mkdirs

    monitor.info(CmdlineMonitor.Verbose, "Extracting zip archive '" + archive + "' to: " + targetDir)

    val partial = !selectedFiles.isEmpty || fileSelector.isDefined
    if (partial) log.debug("Only extracting some content of zip file")

    var filesToExtract = selectedFiles
    var extractedFilesInv: List[File] = Nil

    try {
      val zipIs = new ZipInputStream(new FileInputStream(archive))
      var zipEntry = zipIs.getNextEntry
      val finished = partial && fileSelector.isEmpty && filesToExtract.isEmpty
      while (zipEntry != null && !finished) {
        val extractFile: Option[File] = if (partial) {
          if (!zipEntry.isDirectory) {
            val candidate = filesToExtract.find { case (name, _) => name == zipEntry.getName }
            if (candidate.isDefined) {
              filesToExtract = filesToExtract.filterNot(_ == candidate.get)
              if (candidate.get._2 != null) {
                Some(candidate.get._2)
              } else {
                val full = zipEntry.getName
                val index = full.lastIndexOf("/")
                val name = if (index < 0) full else full.substring(index)
                Some(new File(targetDir + "/" + name))
              }
            } else {
              fileSelector match {
                case None => None
                case Some(s) => s(zipEntry.getName) match {
                  case false => None
                  case true => Some(new File(targetDir + "/" + zipEntry.getName))
                }
              }
            }
          } else {
            None
          }
        } else {
          if (zipEntry.isDirectory) {
            monitor.info(CmdlineMonitor.Verbose, "  Creating " + zipEntry.getName);
            new File(targetDir + "/" + zipEntry.getName).mkdirs
            None
          } else {
            Some(new File(targetDir + "/" + zipEntry.getName))
          }
        }

        if (extractFile.isDefined) {
          monitor.info(CmdlineMonitor.Verbose, "  Extracting " + zipEntry.getName);
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
          extractedFilesInv ::= targetFile
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

    if (!filesToExtract.isEmpty) {
      throw new FileNotFoundException(s"""Could not found file "${filesToExtract.head._1}" in zip archive "${archive}".""")
    }

    extractedFilesInv.reverse
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