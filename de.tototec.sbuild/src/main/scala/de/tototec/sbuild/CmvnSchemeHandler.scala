package de.tototec.sbuild

import java.io.File

class CmvnSchemeHandler(val downloadPath: String, repos: String*) extends SchemeHandler {

  override def localPath(path: String): String = {
    "file:" + localFile(path).getAbsolutePath
  }

  def localFile(path: String): File = {
    new File(downloadPath + "/" + constructMvnPath(path))
  }

  def constructMvnPath(cmvnUrlPathPart: String): String = {

    val (group, artifact, versionWithOptions: Array[String]) = cmvnUrlPathPart.split(":", 3).map(_.trim) match {
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

  override def resolve(path: String): Option[Throwable] = {
    val target = localFile(path).getAbsolutePath
    var result: Option[Throwable] = None
    repos.takeWhile(repo => {
      val url = repo + "/" + constructMvnPath(path)
      result = Util.download(url, target)
      result.isDefined || !new File(target).exists
    })
    result
  }

}