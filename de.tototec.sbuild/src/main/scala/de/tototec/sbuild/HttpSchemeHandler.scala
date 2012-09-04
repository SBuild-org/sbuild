package de.tototec.sbuild

import java.io.File
import java.net.URL
import java.io.FileNotFoundException

/**
 * An HTTP-Scheme handler, that will download the given URI into a directory preserving the URi as path.
 * Example:
 * The HttpSchemeHandler is configured to use '.sbuild/http' as download directory
 * The file 'http://example.com/downloads/example.jar' will be downloaded into
 * '.sbuild/http/example.com/downloads/example.jar'
 */
class HttpSchemeHandler(val downloadDir: File, val forceDownload: Boolean = false) extends SchemeHandler {

  var online: Boolean = true

  def url(path: String): URL = new URL("http:" + path)

  override def localPath(path: String): String = "file:" + localFile(path).getPath

  def localFile(path: String): File = {
    url(path)
    // ok, path is a valid URL
    new File(downloadDir, path)
  }

  override def resolve(path: String): ResolveResult = {
    val target = localFile(path)
    if (online) {
      if (!forceDownload && target.exists) {
        ResolveResult(true, None)
      } else {
        val url = this.url(path)
        println("Downloading " + url + "...")
        val result = Util.download(url.toString, target.getPath)
        result.isDefined || !target.exists
        ResolveResult(false, result)
      }
    } else {
      if (target.exists) ResolveResult(true, None)
      else ResolveResult(false, Option(new FileNotFoundException("File is not present and can not be downloaded in offline-mode: " + target.getPath)))
    }
  }
}