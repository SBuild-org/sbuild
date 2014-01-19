package de.tototec.sbuild

import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest
import scala.Array.canBuildFrom
import de.tototec.sbuild.SchemeHandler.SchemeContext
import de.tototec.sbuild.internal.{ Util => InternalUtil }
import de.tototec.sbuild.internal.StringSplitter
import java.util.regex.Pattern

/**
 * The SchemeHandler to extract resources from a ZIP resource.
 *
 * TODO: Syntax
 *
 *
 * Syntax
 * ------
 *
 *         target ::= <scheme> ':' ( single-file | file-pattern ) ';archive=' <archive>
 *    single-file ::= 'file=' <file> ( ';targetFile=' <file> )?
 *   file-pattern ::= 'pattern=' <pattern>
 *
 *
 * The `scheme` is the name under which the ZipSchemeHandler was registered.
 * The `archive`is the ZIP archive and can be any supported target resolving to a file target.
 *
 * Extracting single files
 * -----------------------
 *
 * The `file` is a the path to the to-be-extracted file in the archive.
 * The optional `targetFile` denotes the location, where the extracted file should be stored.
 *
 * The ZipSchemeHandler will resolve to a file scheme pointing to the extracted file.
 *
 * Examples:
 * {{{
 * // register under the "zip" scheme
 * SchemeHandler("zip", new ZipSchemeHandler())
 *
 * // a target that depends on the MANIFEST.MF file extracted from a local file
 * Target("phony:localTest") dependsOn "zip:file=META-INF/MANIFEST.MF;archive=example-1.0.jar"
 *
 * // a target that depends on the MANIFEST.MF file extracted from a Maven artifact.
 * Target("phony:remoteTest") dependsOn "zip:file=META-INF/MANIFEST.MF;archive=mvn:org.example:example:1.0"
 * }}}
 *
 * Extracting multiple files (no yet implemented)
 * ----------------------------------------------
 *
 * Use `pattern` to specify a regular expression pattern which will match those files from the archive to be extracted.
 * The ZipSchemeHandler will resolve to a phony scheme including all extracted files as attached files.
 * Remember that it is legal for ZIP resources, to not include directory entries, thus your might want to match concrete files inside the archive.
 *
 */
class ZipSchemeHandler(val _baseDir: File = null)(implicit project: Project) extends SchemeResolver with SchemeResolverWithDependencies {

  private[this] def log = Logger[ZipSchemeHandler]

  val baseDir: File = _baseDir match {
    case null => Path(".sbuild/unzip")
    case x => x.getAbsoluteFile
  }

  override def localPath(schemeCtx: SchemeContext): String = parseConfig(schemeCtx.path) match {
    case FileConfig(_, _, targetFile) => "file:" + targetFile.getPath
    case RegexConfig(_, _, _) => "phony:" + schemeCtx.fullName
  }

  override def dependsOn(schemeCtx: SchemeContext): TargetRefs = {
    val config = parseConfig(schemeCtx.path)
    return TargetRefs(TargetRef(config.archive)(project))
  }

  override def resolve(schemeCtx: SchemeContext, targetContext: TargetContext) = {
    targetContext.fileDependencies match {
      case Seq(zipFile) =>

        val config = parseConfig(schemeCtx.path)

        config match {
          case config @ FileConfig(fileInArchive, archiveFile, file) =>

            if (!file.exists || file.lastModified < zipFile.lastModified) try {
              InternalUtil.unzip(zipFile, file.getParentFile, List((fileInArchive -> file)), project.monitor)
              try {
                file.setLastModified(System.currentTimeMillis)
              } catch {
                case e: Exception =>
                  val msg = s"""Could not change lastModified time of extracted file "${file.getPath}"."""
                  log.warn(msg, e)
                  project.monitor.warn(s"""Could not change lastModified time of extracted file "${file.getPath}".""")
                  project.monitor.showStackTrace(CmdlineMonitor.Verbose, e)
              }
            } catch {
              case e: FileNotFoundException =>
                val ex = new ExecutionFailedException(s"""Could not resolve "${targetContext.name}" to "${file}".
${e.getMessage}""", e)
                ex.buildScript = Some(project.projectFile)
                throw ex
            }

          case RegexConfig(archive, regex, extractDir) =>
            val pattern = Pattern.compile(regex)
            val extractedFiles = InternalUtil.unzip(zipFile, extractDir, List(), project.monitor, Some { name: String => pattern.matcher(name).matches() })

            if (!extractedFiles.isEmpty) targetContext.targetLastModified = 1
            extractedFiles.foreach { file =>
              targetContext.attachFile(file)
            }

        }

      case x =>
        // something wrong
        throw new IllegalStateException("Expected exactly one zip file as dependency, but got: " + x)
    }
  }

  private[this] sealed trait Config { def archive: String }
  private[this] case class FileConfig(fileInArchive: String, override val archive: String, targetFile: File) extends Config
  private[this] case class RegexConfig(override val archive: String, regex: String, extractDir: File) extends Config

  private[this] def parseConfig(path: String): Config = {
    val syntax = "[file=fileInArchive;[targetFile=exctractedFile;]|regex=filePathRegex;]archive=archivePath"

    val pairs = StringSplitter.split(path, ";", Some("\\")).map { part =>
      part.split("=", 2) match {
        case Array(key, value) =>
          key match {
            case "archive" | "file" | "targetFile" | "regex" =>
              key -> value
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

    val archive = pairs.get("archive") match {
      case Some(x) => x
      case None =>
        val e = new ProjectConfigurationException("Don't know from which file to extract \"" + path + "\". Please specify 'archive' key.")
        e.buildScript = Some(project.projectFile)
        throw e
    }

    def fileBaseLocation: String = {
      val md = MessageDigest.getInstance("MD5")
      val digestBytes = md.digest(archive.getBytes())
      digestBytes.foldLeft("") { (string, byte) => string + Integer.toString((byte & 0xff) + 0x100, 16).substring(1) }
    }

    (pairs.get("file"), pairs.get("regex")) match {
      case (None, None) =>
        val e = new ProjectConfigurationException("Don't know which file to extract in \"" + path + "\". Either specify 'file' or 'regex' key.")
        e.buildScript = Some(project.projectFile)
        throw e
      case (Some(_), Some(_)) =>
        val e = new ProjectConfigurationException("Keys \"file\" and \"regex\" given at the same time in \"" + path + "\".")
        e.buildScript = Some(project.projectFile)
        throw e
      case (Some(filePath), _) =>
        val file = filePath match {
          case f if f.startsWith("/") => f.substring(1)
          case f => f
        }

        val targetFile = pairs.get("targetFile") match {
          case Some(targetFile) => Path(targetFile)
          case None => Path.normalize(new File(file), new File(baseDir, fileBaseLocation))
        }

        FileConfig(fileInArchive = file, archive = archive, targetFile = targetFile)

      case (_, Some(regex)) =>
        RegexConfig(archive = archive, regex = regex, extractDir = new File(baseDir, fileBaseLocation))
    }

  }

  private[this] def zipFile(config: Config): File = {
    val targetRef = TargetRef(config.archive)(project)
    val target = project.findTarget(targetRef, searchInAllProjects = true) match {
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