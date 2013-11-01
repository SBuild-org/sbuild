package de.tototec.sbuild

import java.io.File
import java.io.FileNotFoundException
import java.net.URL

import de.tototec.sbuild.SchemeHandler.SchemeContext
import de.tototec.sbuild.internal.{Util => InternalUtil}

/**
 * An HTTP-Scheme handler, that will download the given URI into a directory preserving the URI as path.
 * Example:
 * The HttpSchemeHandler is configured to use '.sbuild/http' as download directory
 * The file 'http://example.com/downloads/example.jar' will be downloaded into
 * '.sbuild/http/example.com/downloads/example.jar'
 */
class HttpSchemeHandler(downloadDir: File = null,
                        forceDownload: Boolean = false)(implicit project: Project) extends HttpSchemeHandlerBase(
  Option(downloadDir).getOrElse(Path(".sbuild/http")),
  forceDownload)
    with SchemeResolver {

  override def resolve(schemeCtx: SchemeContext, targetContext: TargetContext) = {
    val lastModified = download(schemeCtx.path, project.monitor)
    targetContext.targetLastModified = lastModified
  }

}

class HttpSchemeHandlerBase(val downloadDir: File, val forceDownload: Boolean = false) extends SchemeHandler {

  var online: Boolean = true

  private val userAgent = s"SBuild/${SBuildVersion.osgiVersion} (HttpSchemeHandler)"

  def url(path: String): URL = new URL("http:" + path)

  override def localPath(schemeCtx: SchemeContext): String = "file:" + localFile(schemeCtx.path).getPath

  def localFile(path: String): File = {
    url(path)
    // ok, path is a valid URL
    new File(downloadDir, path)
  }

  /**
   * @return <code>true</code>, if the file was already up-to-date
   */
  def download(path: String, monitor: CmdlineMonitor): Long = {
    val target = localFile(path)
    if (online) {
      if (!forceDownload && target.exists) {
        target.lastModified
      } else {
        val url = this.url(path)
        //        println("Downloading " + url + "...")
        InternalUtil.download(url.toString, target.getPath, monitor, Some(userAgent)) match {
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