package de.tototec.sbuild

import java.io.File

class ZipSchemeHandler(val _baseDir: File = null)(implicit project: Project) extends SchemeHandlerWithDependencies {

  val baseDir: File = _baseDir match {
    case null => Path(".sbuild/unzip")
    case x => x
  }

  override def localPath(path: String): String = {
    val config = parseConfig(path)
    val targetFile = localFile(config).getAbsoluteFile.getCanonicalFile
    "file:" + targetFile.getPath
  }

  def localFile(config: Config): File = new File(baseDir, config.targetFile)

  override def dependsOn(path: String): TargetRefs = {
    val config = parseConfig(path)
    return TargetRefs(TargetRef(config.archive)(project))
  }

  override def resolve(path: String, targetContext: TargetContext) = {
    val config = parseConfig(path)
    val file = localFile(config)
    
    targetContext.fileDependencies match {
      case Seq(zipFile) =>
        Util.unzip(zipFile, file.getParentFile, List((config.fileInArchive -> file)))
      case x =>
        // something wrong
        throw new IllegalStateException("Expected exactly one zip file as dependency, but got: " + x)
    }
  }

  case class Config(fileInArchive: String, archive: String, targetFile: String)

  def parseConfig(path: String): Config = {
    val syntax = "archive=archivePath;file=fileInArchive"

    val pairs = path.split(";").map { part =>
      part.split("=", 2) match {
        case Array(key, value) =>
          key match {
            case "archive" | "file" | "targetFile" => (key -> value)
            case _ =>
              val e = new ProjectConfigurationException("Unsupported key in key=value pair \"" + part + "\".")
              e.buildScript = Some(project.projectFile)
              throw e
          }
        case _ =>
          val e = new ProjectConfigurationException("Expected a key=value pair, but got \"" + part + "\".")
          e.buildScript = Some(project.projectFile)
          throw e
      }
    }.toMap

    val file = pairs("file") match {
      case f if f.startsWith("/") => f.substring(1)
      case f => f
    }
    val archive = pairs("archive")
    val targetFile = pairs.getOrElse("targetFile", file)
    
    Config(fileInArchive = file, archive = archive, targetFile = targetFile)
  }

  def zipFile(config: Config): File = {
    val targetRef = TargetRef(config.archive)(project)
    val target = project.findTarget(targetRef, true) match {
      case Some(t) => t
      case None => project.findOrCreateTarget(targetRef, true)
    }

    if (target.phony) {
      val e = new ProjectConfigurationException("Referenced archive is phony: " + config.archive)
      e.buildScript = Some(project.projectFile)
      throw e
    }

    target.file
  }

}