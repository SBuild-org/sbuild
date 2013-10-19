package de.tototec.sbuild

import java.io.File
import java.net.URI
import java.net.MalformedURLException
import scala.collection

trait Target {
  /**
   * The unique file resource this target represents.
   * This file might be not related to the target at all, if the target is phony.
   */
  def file: File
  /**
   * The file this target produces.
   * `None` if this target is phony.
   */
  def targetFile: Option[File]
  /** The name of this target. */
  def name: String
  /**
   * If `true`, this target does not (necessarily) produces a file resource with the same name.
   * A phony target can therefore not profit from the advanced up-to-date checks as files can.
   * <p/>
   * E.g. a "clean" target might delete various resources but will most likely not create the file "clean",
   * so it has to be phony.
   * Otherwise, if a file or directory with the same name ("clean" here) exists,
   * it would be used to check, if the target needs to run or not.
   */
  def phony: Boolean

  /**
   * A prerequisites (dependencies) to this target. SBuild will ensure,
   * that these dependency are up-to-date before this target will be executed.
   */
  def dependsOn(goals: TargetRefs): Target
  /**
   * Get a list of all (current) prerequisites (dependencies) of this target.
   */
  private[sbuild] def dependants: TargetRefs

  /**
   * Apply an block of actions, that will be executed, if this target was requested but not up-to-date.
   */
  def exec(execution: => Any): Target
  def exec(execution: TargetContext => Any): Target
  private[sbuild] def action: TargetContext => Any

  /**
   * Set a descriptive information text to this target, to assist the developer/user of the project.
   */
  def help(help: String): Target
  /**
   * Get the assigned help message.
   */
  def help: String

  private[sbuild] def project: Project

  private[sbuild] def isImplicit: Boolean

  def isCacheable: Boolean
  def cacheable: Target
  def setCacheable(cacheable: Boolean): Target

  def evictsCache: Option[String]
  def evictCache: Target
  def evictCache(cacheName: String): Target

  private[sbuild] def isTransparentExec: Boolean

  private[sbuild] def isSideeffectFree: Boolean

  /**
   *  A formatted textual representation of this target relative to a base project.
   *  @since 0.5.0.9002
   */
  def formatRelativeTo(baseProject: Project): String
  /**
   *  A formatted textual representation of this target relative to the base project.
   *  @since 0.6.0.9001
   */
  def formatRelativeToBaseProject: String
}

object Target {
  /**
   * Create a new target with target name.
   */
  def apply(targetRef: TargetRef)(implicit project: Project): Target =
    project.findOrCreateTarget(targetRef)
}

