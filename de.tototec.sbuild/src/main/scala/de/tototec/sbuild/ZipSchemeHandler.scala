package de.tototec.sbuild

import java.io.File
import java.security.MessageDigest
import java.io.FileNotFoundException

class ZipSchemeHandler(val _baseDir: File = null)(implicit project: Project) extends SchemeHandlerWithDependencies {

  val baseDir: File = _baseDir match {
    case null => Path(".sbuild/unzip")
    case x => x.getAbsoluteFile
  }

  override def localPath(path: String): String = {
    val config = parseConfig(path)
    "file:" + config.targetFile.getPath
  }

  override def dependsOn(path: String): TargetRefs = {
    val config = parseConfig(path)
    return TargetRefs(TargetRef(config.archive)(project))
  }

  override def resolve(path: String, targetContext: TargetContext) = {
    val config = parseConfig(path)
    val file = config.targetFile

    targetContext.fileDependencies match {
      case Seq(zipFile) =>
        if (!file.exists) {
          try {
            Util.unzip(zipFile, file.getParentFile, List((config.fileInArchive -> file)))
          } catch {
            case e: FileNotFoundException =>
              val ex = new ExecutionFailedException(s"""Could not resolve "${targetContext.name}" to "${file}".
${e.getMessage}""", e)
              ex.buildScript = Some(project.projectFile)
              throw ex
          }
        }
      case x =>
        // something wrong
        throw new IllegalStateException("Expected exactly one zip file as dependency, but got: " + x)
    }
  }

  case class Config(fileInArchive: String, archive: String, targetFile: File)

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

    def fileBaseLocation: String = {
      val md = MessageDigest.getInstance("MD5")
      val digestBytes = md.digest(archive.getBytes())
      digestBytes.foldLeft("") { (string, byte) => string + Integer.toString((byte & 0xff) + 0x100, 16).substring(1) }
    }

    val targetFile = pairs.get("targetFile") match {
      case Some(targetFile) => Path(targetFile)
      case None => Path.normalize(new File(file), new File(baseDir, fileBaseLocation))
    }

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