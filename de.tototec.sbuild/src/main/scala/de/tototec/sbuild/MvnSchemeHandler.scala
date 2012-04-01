package de.tototec.sbuild

import java.io.File
import java.io.FileNotFoundException

class MvnSchemeHandler(val downloadPath: String = System.getProperty("user.home", ".") + "/.m2/repository", repos: Seq[String] = Seq("http://repo1.maven.org/maven2/")) extends SchemeHandler {

  protected var provisionedResources: Map[String, String] = Map()

  override def localPath(path: String): String = {
    "file:" + localFile(path).getAbsolutePath
  }

  def localFile(path: String): File = {
    provisionedResources.get(path) match {
      case Some(file) => new File(file)
      case None => new File(downloadPath + "/" + constructMvnPath(path))
    }
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

  override def resolve(path: String): Option[Throwable] = {
    provisionedResources.get(path) map {
      case _ => return None
    }

    val target = localFile(path).getAbsoluteFile
    if (online && repos.size > 0) {
      println("Downloading " + path + "...")
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

  /**
   * Provisioning of an existing resource under Maven group:artifact:verion coordinated.
   * When the provisioned resource is requested, no download will happen. The provisioned file will be resolved instead.
   */
  def provision(gav: String, file: String) {
    provisionedResources += (gav -> file)
  }

}