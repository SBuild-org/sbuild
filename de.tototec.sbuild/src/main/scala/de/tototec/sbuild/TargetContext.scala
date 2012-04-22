package de.tototec.sbuild

import java.io.File
import java.util.Date

/**
 * While a target execution, this class can be used to get relevant information
 * about the current target execution and interact with the executor.
 * 
 * TODO: Construct with real dependencies of type target instead of the (miss-)interpretable TargetRefs.
 */
class TargetContext(target: Target) {

  def name = target.name

  /**
   * The file this targets produces, or <code>None</code> if this target is phony.
   */
  def targetFile: Option[File] = target.targetFile

  def start = startTime match {
    case null => startTime = new Date()
    case _ =>
  }
  private var startTime: Date = _

  def end = endTime match {
    case null => endTime = new Date()
    case _ =>
  }
  private var endTime: Date = _

  /**
   * The time in milliseconds this target took to execute.
   * In case this target is still running, the time since it started.
   */
  def execDurationMSec: Long = startTime match {
    case null => 0
    case _ =>
      (endTime match {
        case null => new Date()
        case x => x
      }).getTime - startTime.getTime
  }

  /**
   * The prerequisites (direct dependencies) of this target.
   */
  def prerequisites: Seq[TargetRef] = target.dependants

  def fileDependencies: Seq[File] = target.dependants map { t =>
    target.project.findTarget(t, true) match {
      case None => throw new ProjectConfigurationException("Missing dependency: " + t.name)
      case Some(found) => found.targetFile match {
        case None => null
        case Some(f) => f
      }
    }
  } filter { f => f != null }

  /**
   * Set this to <code>true</code>, if this target execution did not produced anything new,
   * which means, the target was already up-to-date.
   * In later versions, SBuild will honor this setting, and might be able to skip targets,
   * that depend on this on.
   */
  private var _targetWasUpToDate: List[Boolean] = List()
  def targetWasUpToDate = _targetWasUpToDate match {
    case List() => false
    case x => x.forall(_)
  }
  def targetWasUpToDate_=(targetWasUpToDate: Boolean) = _targetWasUpToDate = List(targetWasUpToDate)
  def addToTargetWasUpToDate(targetWasUpToDate: Boolean) = _targetWasUpToDate ::= targetWasUpToDate

}