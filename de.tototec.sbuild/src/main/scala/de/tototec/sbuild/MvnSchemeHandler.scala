package de.tototec.sbuild

import java.io.File
import java.io.FileNotFoundException
import de.tototec.sbuild.SchemeHandler.SchemeContext

object MavenSupport {
  object MavenGav {
    def apply(artifact: String): MavenGav = {
      val (groupId, artifactId, versionWithOptions: Array[String]) = artifact.split(":", 3).map(_.trim) match {
        case Array(g, a, versionWithOptions) => (g, a, versionWithOptions.split(";", 2).map(_.trim))
        case _ => throw new RuntimeException("Invalid format. Format must be: groupId:artifactId:version[;key=val]*")
      }

      val version = versionWithOptions(0)
      val options: Map[String, String] = versionWithOptions match {
        case Array(version, options) => options.split(";").map(_.trim).map(_.split("=", 2).map(_.trim)).map(
          _ match {
            case Array(a) => ((a, "true"))
            case Array(a, b) => ((a, b))
          }).toMap
        case _ => Map()
      }

      MavenGav(groupId, artifactId, version, options.get("classifier"))
    }
  }
  case class MavenGav(groupId: String, artifactId: String, version: String, classifier: Option[String])

}

/**
 * A SchemeHandler able to download Maven artifacts from a set of Maven repositories.
 * Normally, one would register this handler with the "mvn" scheme.
 * Another typical use case is to separate public repositories from non-public (internal) ones,
 * e.g. by registering this handler once under a "mvnPublic" schema and then under a "mvnInternal" scheme,
 * with appropriate sets of repositories, though.
 *
 * Format: groupId:artifactId:version[;key=val]*
 * Supported values for key: classifier
 *
 */
class MvnSchemeHandler(
  val downloadPath: File = new File(System.getProperty("user.home", ".") + "/.m2/repository"),
  repos: Seq[String] = Seq("http://repo1.maven.org/maven2/"))(implicit project: Project)
    extends SchemeResolver {

  private val userAgent = s"SBuild/${SBuildVersion.osgiVersion} (MvnSchemeHandler)"

  import MavenSupport._

  override def localPath(schemeCtx: SchemeContext): String = {
    "file:" + localFile(schemeCtx.path).getAbsolutePath
  }

  def localFile(path: String): File = {
    new File(downloadPath, constructMvnPath(path))
  }

  var online = true

  def constructMvnPath(mvnUrlPathPart: String): String = MavenGav(mvnUrlPathPart) match {
    case MavenGav(group, artifact, version, classifier) =>
      val classifierPart = classifier.map("-" + _).getOrElse("")
      group.replaceAllLiterally(".", "/") + "/" + artifact + "/" +
        version + "/" + artifact + "-" + version + classifierPart + ".jar"
  }

  override def resolve(schemeCtx: SchemeContext, targetContext: TargetContext) = {
    val target = localFile(schemeCtx.path).getAbsoluteFile
    if (online && repos.size > 0) {
      var result: Option[Throwable] = None
      repos.takeWhile(repo => {
        val url = repo + "/" + constructMvnPath(schemeCtx.path)
        result = Util.download(url, target.getPath, project.log, Some(userAgent))
        val failed = result.isDefined || !target.exists
        if (failed) project.log.log(LogLevel.Info, "Download failed.")
        failed
      })
      result match {
        case Some(e) => throw e
        case _ =>
      }
      targetContext.targetLastModified = target.lastModified
    } else {
      if (target.exists) {
        targetContext.targetLastModified = target.lastModified
      } else {
        throw new FileNotFoundException("File is not present and can not be downloaded in offline-mode: " + target.getPath)
      }
    }
  }

}

