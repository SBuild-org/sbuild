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
   * <code>None</code> if this target is phony.
   */
  def targetFile: Option[File]
  /** The name of this target. */
  def name: String
  /**
   * If <code>true</code>, this target does not (necessarily) produces a file resource with the same name.
   * A phony target can therefore not profit from the advanced up-to-date checks as files can.
   * <p/>
   * E.g. a "clean' target might delete various resources but will most likely not create a "clean" file,
   * so it has to be phony.
   * Otherwise, if a file or directory with the same name ("clean" here) exists,
   * it would be used to check, if the target needs to run or not.
   */
  def phony: Boolean

  /**
   * A prerequisites (dependencies) to this target. Sbuild will ensure,
   * that these dependency are up-to-date before this target will be executed.
   */
  def dependsOn(goals: => TargetRefs): Target
  /**
   * Get a list of all (current) prerequisites (dependencies) of this target.
   */
  private[sbuild] def dependants: Seq[TargetRef]

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
  //  /**
  //   * Default: use the file to which the target name resolves.
  //   * If pattern is specified, we produce files matching the given pattern.
  //   * If given "" as pattern, the goal is "phony", which means, the output cannot be checked by sbuild to actuality.
  //   */
  //  def produces(pattern: String): Target
  //  def needsToExec(needsToExec: => Boolean): Target

  private[sbuild] def project: Project

  private[sbuild] def isImplicit: Boolean

}

object Target {
  def apply(targetRef: TargetRef)(implicit project: Project): Target = project.findOrCreateTarget(targetRef)
}

case class ProjectTarget private[sbuild] (override val name: String,
                                          override val file: File,
                                          override val phony: Boolean,
                                          handler: Option[SchemeHandler],
                                          override val project: Project) extends Target {

  private[sbuild] var isImplicit = false

  private var _exec: TargetContext => Any = handler match {
    case None => null
    case Some(handler) => { ctx: TargetContext =>
      handler.resolve(TargetRef(name)(project).nameWithoutProto) match {
        case ResolveResult(upToDate, None) =>
          ctx.targetWasUpToDate = upToDate
        case ResolveResult(_, Some(t)) =>
          throw t
      }
    }
  }
  private var _help: String = _
  private var prereqs = Seq[TargetRef]()

  private[sbuild] override def action = _exec
  private[sbuild] override def dependants = prereqs

  override def dependsOn(targetRefs: => TargetRefs): Target = {
    prereqs ++= targetRefs.targetRefs
    ProjectTarget.this
  }

  override def exec(execution: => Any): Target = {
    _exec = _ => execution
    this
  }
  override def exec(execution: TargetContext => Any): Target = {
    _exec = execution
    this
  }

  override def help(help: String): Target = {
    this._help = help
    this
  }
  override def help: String = _help

  override def toString() = {
    def hasExec = _exec match {
      case null => "non"
      case _ => "defined"
    }
    "Target(" + TargetRef(name)(project).nameWithoutProto + "=>" + file + (if (phony) "[phony]" else "") + ", dependsOn=" + prereqs.map(t => t.name).mkString(",") + ", exec=" + hasExec + ")"
  }

  lazy val targetFile: Option[File] = phony match {
    case false => Some(file)
    case true => None
  }

}
