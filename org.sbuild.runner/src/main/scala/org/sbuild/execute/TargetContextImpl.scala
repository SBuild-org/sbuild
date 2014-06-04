package org.sbuild.execute

import java.io.File
import java.io.FileNotFoundException
import java.util.Date
import org.sbuild.Project
import org.sbuild.Target
import org.sbuild.TargetContext
import org.sbuild.TargetRef

class TargetContextImpl(
  override val target: Target,
  override val prerequisitesLastModified: Long,
  private[sbuild] val directDepsTargetContexts: Seq[TargetContext])
    extends TargetContext {

  override def name = target.name

  /**
   * The file this targets produces, or <code>None</code> if this target is phony.
   */
  override def targetFile: Option[File] = target.targetFile

  def start = startTime match {
    case null => startTime = new Date()
    case _ =>
  }
  private[this] var startTime: Date = _

  def end = _endTime match {
    case null => _endTime = new Date()
    case _ =>
  }
  private[this] var _endTime: Date = _
  private[sbuild] def endTime: Option[Date] = Option(_endTime)

  /**
   * The time in milliseconds this target took to execute.
   * In case this target is still running, the time since it started.
   */
  override def execDurationMSec: Long = startTime match {
    case null => 0
    case _ =>
      (endTime match {
        case None => new Date()
        case Some(x) => x
      }).getTime - startTime.getTime
  }

  /**
   * The prerequisites (direct dependencies) of this target.
   */
  override def prerequisites: Seq[TargetRef] = target.dependants.targetRefs

  override def fileDependencies: Seq[File] = files
  override def files: Seq[File] = directDepsTargetContexts.flatMap(_.targetFiles)

  private var _targetLastModified: Option[Long] = None
  override def targetLastModified: Option[Long] = _targetLastModified
  override def targetLastModified_=(targetLastModified: Long) = _targetLastModified =
    // TODO: consider a 0L as argument could mean, file does not exists and this could mean, we are not up-to-date
    // TODO: So, a lastModified of 0L should replaced by a lastModified as of NOW
    _targetLastModified match {
      case Some(previous) => Some(math.max(previous, targetLastModified))
      case None if targetLastModified > 0 =>
        val now = System.currentTimeMillis
        // record this lastModified
        // and also consider lastModified of attached files
        val lm = _attachedFiles.foldLeft(targetLastModified) { (lm, file) =>
          if (file.lastModified > now) {
            // TODO: consider an offset of about 3 seconds (as Make does)
            target.project.monitor.warn(s"""Modification time of file "${file}" is in the future.""")
          }
          math.max(lm, file.lastModified)
        }
        Some(lm)
      case _ => None
    }

  override def project: Project = target.project

  private[sbuild] var _attachedFiles: Seq[File] = Seq()

  override def attachedFiles: Seq[File] = _attachedFiles
  override def attachFile(file: File) {
    attachFileWithoutLastModifiedCheck(Seq(file))
    // If we have already a set lastModified, than update it now
    if (targetLastModified.isDefined) {
      val now = System.currentTimeMillis
      if (file.lastModified > now) {
        // TODO: consider an offset of about 3 seconds (as Make does)
        target.project.monitor.warn(s"""Modification time of file "${file}" is in the future.""")
      }
      targetLastModified = file.lastModified
    }
  }
  private[sbuild] def attachFileWithoutLastModifiedCheck(files: Seq[File]) {
    files.foreach { file =>
      if (!file.exists)
        throw new FileNotFoundException(s"""Attached file "${file.getPath}" does not exist.""")
    }
    _attachedFiles ++= files
  }

  private[this] var out: String = ""
  def stdout: String = out
  def stdout_=(out: String): Unit = this.out = out

  private[this] var err: String = ""
  def stderr: String = err
  def stderr_=(err: String): Unit = this.err = err

  override def toString(): String = getClass.getSimpleName() +
    "(name=" + name +
    ",targetFile=" + targetFile +
    ",target=" + target +
    ",prerequisitesLastModified=" + prerequisitesLastModified +
    ",directDepsTargetContexts[names]=" + directDepsTargetContexts.map(_.name) +
    ")"

}
