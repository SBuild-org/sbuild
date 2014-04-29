package de.tototec.sbuild

import java.io.File
import java.io.FileNotFoundException
import java.net.Proxy
import java.net.URL
import scala.annotation.implicitNotFound
import de.tototec.sbuild.SchemeHandler.SchemeContext
import de.tototec.sbuild.internal.I18n
import de.tototec.sbuild.internal.{ Util => InternalUtil }
import java.net.InetAddress
import java.net.InetSocketAddress
import scala.util.Try

object HttpSchemeHandler {
  def autoProxy(proxy: Option[Proxy], project: Project): Proxy = proxy match {
    case Some(p) => p
    case None =>
      // autodetect proxy settings

      // host:port
      // http://host:port
      val HttpProxy = "(http://)?([^:]+):([0-9]+)".r

      Logger[HttpSchemeHandler.type].debug("Trying to autodetect HTTP proxy settings")

      System.getenv("http_proxy") match {
        case null => Proxy.NO_PROXY
        case HttpProxy(_, host, port) if Try(port.toInt).isSuccess =>
          new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port.toInt))
        case p => // unsupported proxy settings
          project.monitor.warn(s"The content of the 'http_proxy' environment variable is not supported: '${p}'")
          Proxy.NO_PROXY
      }
  }
}

/**
 * An HTTP-Scheme handler, that will download the given URI into a directory preserving the URI as path.
 * Example:
 * The HttpSchemeHandler is configured to use '.sbuild/http' as download directory
 * The file 'http://example.com/downloads/example.jar' will be downloaded into
 * '.sbuild/http/example.com/downloads/example.jar'
 */
class HttpSchemeHandler(downloadDir: File = null,
                        forceDownload: Boolean = false,
                        proxy: Option[Proxy] = None)(implicit project: Project) extends HttpSchemeHandlerBase(
  downloadDir = Option(downloadDir).getOrElse(Path(".sbuild/http")),
  forceDownload = forceDownload,
  proxy = HttpSchemeHandler.autoProxy(proxy, project))
    with SchemeResolver {

  override def resolve(schemeCtx: SchemeContext, targetContext: TargetContext) = {
    val lastModified = download(schemeCtx.path, project.monitor)
    targetContext.targetLastModified = lastModified
  }

}

class HttpSchemeHandlerBase(
    val downloadDir: File,
    val forceDownload: Boolean = false,
    val proxy: Proxy = Proxy.NO_PROXY) extends SchemeHandler {

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
   * @return The last modified time stamp of the file.
   */
  def download(path: String, monitor: CmdlineMonitor): Long = {
    val target = localFile(path)
    if (online) {
      if (!forceDownload && target.exists) {
        target.lastModified
      } else {
        val url = this.url(path)
        //        println("Downloading " + url + "...")
        InternalUtil.download(url.toString, target.getPath, monitor, Some(userAgent), proxy = proxy) match {
          case Some(e) => throw e
          case _ => target.lastModified
        }
      }
    } else {
      if (target.exists) {
        target.lastModified
      } else {
        val msg = I18n.marktr("File is not present and can not be downloaded in offline-mode: {0}")
        throw new FileNotFoundException(I18n.notr(msg, target.getPath)) {
          override def getLocalizedMessage: String = I18n[HttpSchemeHandlerBase].tr(msg, target.getPath)
        }
      }
    }
  }
}