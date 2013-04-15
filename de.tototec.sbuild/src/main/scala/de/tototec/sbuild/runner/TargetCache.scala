package de.tototec.sbuild.runner

import java.io.File
import scala.io.Source
import de.tototec.sbuild.TargetContext
import de.tototec.sbuild.LogLevel
import scala.util.Try
import de.tototec.sbuild.TargetContextImpl
import java.io.FileWriter
import de.tototec.sbuild.Project
import de.tototec.sbuild.Path
import de.tototec.sbuild.Util
import java.security.MessageDigest

class TargetCache {

  case class CachedState(targetLastModified: Long, attachedFiles: Seq[File])

  def cacheStateDir(project: Project): File =
    Path(".sbuild/scala/" + project.projectFile.getName + "/cache")(project)

  def cacheStateFile(project: Project, cacheName: String): File = {
    val md = MessageDigest.getInstance("MD5")
    val digestBytes = md.digest(cacheName.replaceFirst("^phony:", "").getBytes())
    val md5 = digestBytes.foldLeft("") { (string, byte) => string + Integer.toString((byte & 0xff) + 0x100, 16).substring(1) }

    new File(cacheStateDir(project), md5)
  }

  def loadOrDropCachedState(ctx: TargetContext): Option[CachedState] = {
    // TODO: check same prerequisites, 
    // check same fileDependencies, 
    // check same lastModified of fileDependencies, 
    // check same lastModified
    // TODO: if all is same, return cached values

    ctx.project.log.log(LogLevel.Debug, "Checking execution state of target: " + ctx.name)

    var cachedPrerequisitesLastModified: Option[Long] = None
    var cachedFileDependencies: Set[File] = Set()
    var cachedPrerequisites: Seq[String] = Seq()
    var cachedTargetLastModified: Option[Long] = None
    var cachedAttachedFiles: Seq[File] = Seq()

    //    val stateDir = Path(".sbuild/scala/" + ctx.target.project.projectFile.getName + "/cache")(ctx.project)
    //    val stateFile = new File(stateDir, ctx.name.replaceFirst("^phony:", ""))
    val stateFile = cacheStateFile(ctx.project, ctx.name)
    if (!stateFile.exists) {
      ctx.project.log.log(LogLevel.Debug, s"""No execution state file for target "${ctx.name}" exists.""")
      return None
    }

    var mode = ""

    val source = Source.fromFile(stateFile)
    def closeAndDrop(reason: => String) {
      ctx.project.log.log(LogLevel.Debug, s"""Execution state file for target "${ctx.name}" exists, but is not up-to-date. Reason: ${reason}""")
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
            ctx.project.log.log(LogLevel.Warn, s"""Unexpected file format detected in file "${stateFile}". Dropping cached state of target "${ctx.name}".""")
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

  def writeCachedState(ctx: TargetContextImpl) {
    // TODO: robustness
    //    val stateDir = Path(".sbuild/scala/" + ctx.target.project.projectFile.getName + "/cache")(ctx.project)
    //    stateDir.mkdirs
    //    val stateFile = new File(stateDir, ctx.name.replaceFirst("^phony:", ""))

    val stateFile = cacheStateFile(ctx.project, ctx.name)
    stateFile.getParentFile() match {
      case pf: File => pf.mkdirs()
      case _ => // strage
    }

    val writer = new FileWriter(stateFile)

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

    writer.close
    ctx.project.log.log(LogLevel.Debug, s"""Wrote execution cache state file for target "${ctx.name}" to ${stateFile}.""")

  }

  def dropCacheState(project: Project, cacheName: String) {
    cacheName match {
      case "*" => dropAllCacheState(project)
      case cache =>
        val stateDir = Path(".sbuild/scala/" + project.projectFile.getName + "/cache")(project)
        Util.delete(stateDir)
    }
  }

  def dropAllCacheState(project: Project): Unit = Util.delete(cacheStateDir(project))

}