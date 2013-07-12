package de.tototec.sbuild

import java.io.File
import java.net.URL
import java.io.FileNotFoundException

/**
 * An HTTP-Scheme handler, that will download the given URI into a directory preserving the URI as path.
 * Example:
 * The HttpSchemeHandler is configured to use '.sbuild/http' as download directory
 * The file 'http://example.com/downloads/example.jar' will be downloaded into
 * '.sbuild/http/example.com/downloads/example.jar'
 */
class HttpSchemeHandler(downloadDir: File = null,
                        forceDownload: Boolean = false)(implicit project: Project) extends HttpSchemeHandlerBase(
  downloadDir match {
    case null => Path(".sbuild/http")
    case x => x
  },
  forceDownload)
    with SchemeResolver {

  override def resolve(path: String, targetContext: TargetContext) = {
    val lastModified = download(path, project.log)
    targetContext.targetLastModified = lastModified
  }

}

class HttpSchemeHandlerBase(val downloadDir: File, val forceDownload: Boolean = false) extends SchemeHandler {

  var online: Boolean = true

  def url(path: String): URL = new URL("http:" + path)

  override def localPath(path: String): String = "file:" + localFile(path).getPath

  def localFile(path: String): File = {
    url(path)
    // ok, path is a valid URL
    new File(downloadDir, path)
  }

  /**
   * @return <code>true</code>, if the file was already up-to-date
   */
  def download(path: String, log: SBuildLogger): Long = {
    val target = localFile(path)
    if (online) {
      if (!forceDownload && target.exists) {
        target.lastModified
      } else {
        val url = this.url(path)
        //        println("Downloading " + url + "...")
        Util.download(url.toString, target.getPath, log) match {
          case Some(e) => throw e
          case _ => target.lastModified
        }
      }
    } else {
      if (target.exists) {
        target.lastModified
      } else {
        throw new FileNotFoundException("File is not present and can not be downloaded in offline-mode: " + target.getPath)
      }
    }
  }
}