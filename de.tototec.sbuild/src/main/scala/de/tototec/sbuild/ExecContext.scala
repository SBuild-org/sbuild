package de.tototec.sbuild

import java.io.File
import java.util.Date

/**
 * While a target execution, this class can be used to get relevant information
 * about the current target execution and interact with the executor.
 */
class ExecContext(target: Target, val startTime: Date = new Date()) {

  /**
   * The file this targets produces, or <code>None</code> if this target is phony.
   */
  def targetFile: Option[File] = target.targetFile

  private[sbuild] var endTime: Date = _

  /**
   * The time in milliseconds this target took to execute.
   * In case this target is still running, the time since it started.
   */
  def execDurationMSec: Long = (endTime match {
    case null => new Date()
    case x => x
  }).getTime - startTime.getTime

  /**
   * The prerequisites (direct dependencies) of this target.
   */
  def prerequisites: Seq[TargetRef] = target.dependants

  /**
   * Set this to <code>true</code>, if this target execution did not produced anything new,
   * which means, the target was already up-to-date.
   * In later versions, SBuild will honor this setting, and might be able to skip targets,
   * that depend on this on.
   */
  var targetWasUpToDate: Boolean = false

}