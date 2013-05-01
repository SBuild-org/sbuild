package de.tototec.sbuild

import java.io.File
import java.io.FileNotFoundException

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

  override def localPath(path: String): String = {
    "file:" + localFile(path).getAbsolutePath
  }

  def localFile(path: String): File = {
    new File(downloadPath, constructMvnPath(path))
  }

  var online = true

  def constructMvnPath(mvnUrlPathPart: String): String = {

    val (group, artifact, versionWithOptions: Array[String]) = mvnUrlPathPart.split(":", 3).map(_.trim) match {
      case Array(group, artifact, versionWithOptions) => (group, artifact, versionWithOptions.split(";", 2).map(_.trim))
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

    val classifierPart = options.get("classifier") match {
      case Some(x) => "-" + options("classifier")
      case None => ""
    }

    group.replaceAllLiterally(".", "/") + "/" + artifact + "/" +
      version + "/" + artifact + "-" + version + classifierPart + ".jar"
  }

  override def resolve(path: String, targetContext: TargetContext) = {
    val target = localFile(path).getAbsoluteFile
    if (online && repos.size > 0) {
      var result: Option[Throwable] = None
      repos.takeWhile(repo => {
        val url = repo + "/" + constructMvnPath(path)
        result = Util.download(url, target.getPath, project.log)
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

