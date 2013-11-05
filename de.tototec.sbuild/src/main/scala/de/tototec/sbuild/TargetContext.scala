package de.tototec.sbuild

import java.io.File
import java.util.Date
import java.io.FileNotFoundException

/**
 * While a target is executed, this trait can be used to get relevant information
 * about the current target execution and interact with the executor.
 *
 */
trait TargetContext {
  /** The name of the currently executed target */
  def name: String

  protected[sbuild] def target: Target

  /**
   * The target file (main file) this targets produces, or <code>None</code> if this target is phony.
   */
  def targetFile: Option[File]

  /** The prerequisites (direct dependencies) of this target. */
  def prerequisites: Seq[TargetRef]

  /**
   * Those files, that belongs to dependencies that resolve to files.
   * Dependencies with phony scheme or which resolve to phony will not be included.
   */
  def fileDependencies: Seq[File]

  def prerequisitesLastModified: Long

  /**
   * The time in milliseconds this target took to execute.
   * In case this target is still running, the time since it started.
   */
  def execDurationMSec: Long

  def targetLastModified: Option[Long]
  def targetLastModified_=(targetLastModified: Long)

  def project: Project

  def attachedFiles: Seq[File]
  /**
   * Attach additional files to this target context. The file must exists!
   */
  def attachFile(file: File)

  def targetFiles: Seq[File] = targetFile.toSeq ++ attachedFiles

}

// TODO: decide, if we want that "magic" access
///**
// * Easy access to the [[TargetContext]].
// * Warning: all methods require to be called inside a Target execution block!
// */
//object TargetContext extends TargetContext {
//  override def name: String = WithinTargetExecution.safeTargetContext("TargetContext.name").name
//  override protected[sbuild] def target: Target = WithinTargetExecution.safeTargetContext("TargetContext.name").target
//  override def targetFile: Option[File] = WithinTargetExecution.safeTargetContext("TargetContext.targetFile").targetFile
//  override def prerequisites: Seq[TargetRef] = WithinTargetExecution.safeTargetContext("TargetContext.prerequisites").prerequisites
//  override def fileDependencies: Seq[File] = WithinTargetExecution.safeTargetContext("TargetContext.fileDependencies").fileDependencies
//  override def prerequisitesLastModified: Long = WithinTargetExecution.safeTargetContext("TargetContext.prerequisitesLastModified").prerequisitesLastModified
//  override def execDurationMSec: Long = WithinTargetExecution.safeTargetContext("TargetContext.execDurationMSec").execDurationMSec
//  override def targetLastModified: Option[Long] = WithinTargetExecution.safeTargetContext("TargetContext.targetLastModified").targetLastModified
//  override def targetLastModified_=(targetLastModified: Long) = WithinTargetExecution.safeTargetContext("TargetContext.targetLastModified").targetLastModified_=(targetLastModified)
//  override def project: Project = WithinTargetExecution.safeTargetContext("TargetContext.project").project
//  override def attachedFiles: Seq[File] = WithinTargetExecution.safeTargetContext("TargetContext.attachedFiles").attachedFiles
//  override def attachFile(file: File) = WithinTargetExecution.safeTargetContext("TargetContext.attachFile").attachFile(file)
//  override def targetFiles: Seq[File] = WithinTargetExecution.safeTargetContext("TargetContext.targetFiles").targetFiles
//}

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

  override def fileDependencies: Seq[File] = directDepsTargetContexts.flatMap(_.targetFiles)

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
        throw new FileNotFoundException(s"""Attached file "${file.getPath}" does not exists.""")
    }
    _attachedFiles ++= files
  }

  override def toString(): String = getClass.getSimpleName() +
    "(name=" + name +
    ",targetFile=" + targetFile +
    ",target=" + target +
    ",prerequisitesLastModified=" + prerequisitesLastModified +
    ",directDepsTargetContexts[names]=" + directDepsTargetContexts.map(_.name) +
    ")"

}
