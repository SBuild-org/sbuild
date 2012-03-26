package de.tototec.sbuild

import java.io.File
import java.io.FileNotFoundException

class MvnSchemeHandler(val downloadPath: String = System.getProperty("user.home", ".") + "/.m2/repository", repos: Seq[String] = Seq("http://repo1.maven.org/maven2/")) extends SchemeHandler {

  override def localPath(path: String): String = {
    "file:" + localFile(path).getAbsolutePath
  }

  def localFile(path: String): File = {
    new File(downloadPath + "/" + constructMvnPath(path))
  }

  var online = true

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
    val target = localFile(path).getAbsoluteFile
    if (online) {
      var result: Option[Throwable] = None
      repos.takeWhile(repo => {
        val url = repo + "/" + constructMvnPath(path)
        result = Util.download(url, target.getPath)
        result.isDefined || !target.exists
      })
      result
    } else {
      if (target.exists) None
      else Option(new FileNotFoundException("File is not present and can not be downloaded in offline-mode: " + target.getPath))
    }
  }

}