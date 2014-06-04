package org.sbuild

import java.io.File

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
  def dependsOn: Seq[TargetRef] = prerequisites

  /**
   * Those files, that belongs to dependencies that resolve to files.
   * Dependencies with phony scheme or which resolve to phony and do not have any attached files will not be included.
   */
  // TODO: Think about it: @deprecated("Use files instead", "0.7.9013")
  def fileDependencies: Seq[File]
  /**
   * Those files, that belongs to dependencies that resolve to files.
   * Dependencies with phony scheme or which resolve to phony and do not have any attached files will not be included.
   */
  def files: Seq[File]

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

  def stdout: String
  def stderr: String

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

