package de.tototec.sbuild.execute

import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.security.MessageDigest

import scala.io.Source
import scala.util.Try

import de.tototec.sbuild.Logger
import de.tototec.sbuild.Path
import de.tototec.sbuild.Project
import de.tototec.sbuild.TargetContext
import de.tototec.sbuild.TargetContextImpl
import de.tototec.sbuild.Util

class PersistentTargetCache {

  private[this] val log = Logger[PersistentTargetCache]

  case class CachedState(targetLastModified: Long, attachedFiles: Seq[File])

  def cacheStateDir(project: Project): File =
    Path(".sbuild/scala/" + project.projectFile.getName + "/cache")(project)

  def cacheStateFile(project: Project, cacheName: String): File = {
    val md = MessageDigest.getInstance("MD5")
    val digestBytes = md.digest(cacheName.replaceFirst("^phony:", "").getBytes())
    val md5 = digestBytes.foldLeft("") { (string, byte) => string + Integer.toString((byte & 0xff) + 0x100, 16).substring(1) }

    new File(cacheStateDir(project), md5)
  }

  def loadOrDropCachedState(ctx: TargetContext): Option[CachedState] = synchronized {
    // TODO check same lastModified of fileDependencies, 

    log.debug("Checking execution state of target: " + ctx.name)

    val stateFile = cacheStateFile(ctx.project, ctx.name)
    if (!stateFile.exists) {
      log.debug("No previous execution state file found for target: " + ctx.name)
      return None
    }

    var cachedPrerequisitesLastModified: Option[Long] = None
    var cachedFileDependencies: Set[File] = Set()
    var cachedPrerequisites: Seq[String] = Seq()
    var cachedTargetLastModified: Option[Long] = None
    var cachedAttachedFiles: Seq[File] = Seq()
    var mode = ""

    val source = Source.fromFile(stateFile)
    def closeAndDrop(reason: => String) {
      log.debug(s"""Execution state file for target "${ctx.name}" exists, but is not up-to-date. Reason: ${reason}""")
      source.close
      stateFile.delete
    }

    source.getLines.foreach(line =>
      if (line.startsWith("[")) {
        mode = line
      } else {
        mode match {
          case "[prerequisitesLastModified]" =>
            cachedPrerequisitesLastModified = Try(line.toLong).toOption

          case "[prerequisites]" =>
            cachedPrerequisites ++= Seq(line)

          case "[fileDependencies]" =>
            cachedFileDependencies ++= Set(new File(line))

          case "[attachedFiles]" =>
            val file = new File(line)
            if (!file.exists) {
              closeAndDrop(s"""Attached file "${line}" no longer exists.""")
              return None
            }
            cachedAttachedFiles ++= Seq(file)

          case "[targetLastModified]" =>
            cachedTargetLastModified = Try(line.toLong).toOption

          case unknownMode =>
            log.warn(s"""Unexpected file format detected in file "${stateFile}". Dropping cached state of target "${ctx.name}".""")
            closeAndDrop("Unknown mode: " + unknownMode)
            return None
        }
      }
    )

    source.close

    if (cachedTargetLastModified.isEmpty) {
      closeAndDrop("Cached targetLastModified not defined.")
      return None
    }

    if (ctx.prerequisitesLastModified > cachedPrerequisitesLastModified.get) {
      closeAndDrop("prerequisitesLastModified do not match.")
      return None
    }

    if (ctx.prerequisites.size != cachedPrerequisites.size ||
      ctx.prerequisites.map { _.ref } != cachedPrerequisites) {
      closeAndDrop("prerequisites changed.")
      return None
    }

    val ctxFileDeps = ctx.fileDependencies.toSet
    if (ctxFileDeps.size != cachedFileDependencies.size ||
      ctxFileDeps != cachedFileDependencies) {
      closeAndDrop("fileDependencies changed.")
      return None
    }

    // TODO: also check existence of fileDependencies

    Some(CachedState(targetLastModified = cachedTargetLastModified.get, attachedFiles = cachedAttachedFiles))
  }

  def writeCachedState(ctx: TargetContextImpl): Unit = synchronized {
    val cacheName = ctx.name
    val stateFile = cacheStateFile(ctx.project, cacheName)
    stateFile.getParentFile() match {
      case pf: File => pf.mkdirs()
      case _ => // nothing to create
    }

    val writer = new BufferedWriter(new FileWriter(stateFile))
    try {
      writer.write("[prerequisitesLastModified]\n")
      writer.write(ctx.prerequisitesLastModified + "\n")

      writer.write("[prerequisites]\n")
      ctx.prerequisites.foreach { dep => writer.write(dep.ref + "\n") }

      writer.write("[fileDependencies]\n")
      ctx.fileDependencies.foreach { dep => writer.write(dep.getPath + "\n") }

      writer.write("[targetLastModified]\n")
      val targetLM = ctx.targetLastModified match {
        case Some(lm) => lm
        case _ =>
          ctx.endTime match {
            case Some(x) => x.getTime
            case None => System.currentTimeMillis
          }
      }
      writer.write(targetLM + "\n")

      writer.write("[attachedFiles]\n")
      ctx.attachedFiles.foreach { file => writer.write(file.getPath + "\n") }

    } finally {
      writer.close
    }
    log.debug(s"""Wrote execution cache state file for target "${cacheName}" to ${stateFile}.""")

  }

  def dropCacheState(project: Project, cacheName: String): Unit = synchronized {
    cacheName match {
      case "*" | "" => dropAllCacheState(project)
      case cache => Util.delete(cacheStateFile(project, cacheName))
    }
  }

  def dropAllCacheState(project: Project): Unit = synchronized { Util.delete(cacheStateDir(project)) }

}