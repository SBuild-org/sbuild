package de.tototec.sbuild

import java.io.File
import java.net.URL
import java.io.FileNotFoundException

class HttpSchemeHandler(val downloadDir: File) extends SchemeHandler {

  var online: Boolean = true

  def url(path: String): URL = new URL("http:" + path)

  override def localPath(path: String): String = "file:" + localFile(path).getPath

  def localFile(path: String): File = {
    url(path)
    // ok, path is a valid URL
    new File(downloadDir, path)
  }

  override def resolve(path: String): Option[Throwable] = {
    val target = localFile(path)
    if (online) {
      val url = this.url(path)
      println("Downloading " + url + "...")
      val result = Util.download(url.toString, target.getPath)
      result.isDefined || !target.exists
      result
    } else {
      if (target.exists) None
      else Option(new FileNotFoundException("File is not present and can not be downloaded in offline-mode: " + target.getPath))
    }
  }
}