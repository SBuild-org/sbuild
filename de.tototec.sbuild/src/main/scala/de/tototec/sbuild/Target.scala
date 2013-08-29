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
}

object Target {
  /**
   * Create a new target with target name.
   */
  def apply(targetRef: TargetRef)(implicit project: Project): Target =
    project.findOrCreateTarget(targetRef)
}

class ProjectTarget private[sbuild] (override val name: String,
                                     override val file: File,
                                     override val phony: Boolean,
                                     override val project: Project,
                                     initialPrereqs: Seq[TargetRef] = Seq(),
                                     initialExec: TargetContext => Any = null,
                                     initialTransparentExec: Boolean = false,
                                     initialSideEffectFree: Boolean = false) extends Target {

  private[sbuild] var isImplicit = false

  private var _help: String = _
  private var prereqs: Seq[TargetRef] = initialPrereqs

  private[this] var _transparentExec: Boolean = initialTransparentExec
  override private[sbuild] def isTransparentExec: Boolean = _transparentExec

  private[this] var _sideeffectFree: Boolean = initialSideEffectFree
  override private[sbuild] def isSideeffectFree: Boolean = _sideeffectFree

  private[this] var execReplaced: Boolean = false
  private[this] var _exec: TargetContext => Any = initialExec

  private[sbuild] override def action = _exec
  private[sbuild] override def dependants = prereqs

  override def dependsOn(targetRefs: => TargetRefs): Target = {
    prereqs ++= targetRefs.targetRefs
    ProjectTarget.this
  }

  override def exec(execution: => Any): Target = exec((_: TargetContext) => execution)
  override def exec(execution: TargetContext => Any): Target = {
    if (_exec != null) {
      project.log.log(LogLevel.Warn, s"Warning: Reassignment of exec block for target ${name} in project file ${project.projectFile}")
      execReplaced = true
    }
    // always non-transparent, but in case we override a transparent scheme handler here
    _transparentExec = false
    // always assume side effects
    _sideeffectFree = false
    _exec = execution
    this
  }

  override def help(help: String): Target = {
    this._help = help
    this
  }
  override def help: String = _help

  override def toString() =
    getClass.getSimpleName +
      "(" + name + "=>" + file + (if (phony) "[phony]" else "") +
      ",dependsOn=" + prereqs.map(t => t.name).mkString(" ~ ") +
      ",exec=" + (_exec match {
        case null => "non"
        case _ if execReplaced => "replaced"
        case e if e.eq(initialExec) => "initialized"
        case _ => "defined"
      }) +
      ",isImplicit=" + isImplicit +
      ",isCacheable=" + isCacheable +
      ")"

  lazy val targetFile: Option[File] = phony match {
    case false => Some(file)
    case true => None
  }

  private[this] var _cacheable: Boolean = false
  override def isCacheable: Boolean = _cacheable
  override def setCacheable(cacheable: Boolean): Target = {
    _cacheable = cacheable
    this
  }
  override def cacheable(): Target = {
    _cacheable = true
    this
  }

  private[this] var _evictCache: Option[String] = None

  override def evictsCache: Option[String] = _evictCache

  override def evictCache: Target = {
    _evictCache = Some("")
    this
  }

  override def evictCache(cacheName: String): Target = {
    _evictCache = Some(cacheName)
    this
  }

  def setEvictCache(evictCache: Boolean): Target = {
    _evictCache = evictCache match {
      case true => Some("")
      case false => None
    }
    this
  }

  def canEqual(other: Any): Boolean = other.isInstanceOf[ProjectTarget]
  override def equals(other: Any): Boolean = other match {
    case that: ProjectTarget => that.canEqual(this) &&
      name == that.name &&
      file == that.file &&
      phony == that.phony &&
      project == that.project
    case _ => false
  }
  override def hashCode: Int = Seq(name, file, phony, project).foldLeft(1) { (prev, h) => 41 * prev + h.hashCode }

  def formatRelativeTo(baseProject: Project): String =
    (if (project != baseProject) {
      baseProject.projectDirectory.toURI.relativize(project.projectFile.toURI).getPath + "::"
    } else "") + TargetRef(this).nameWithoutStandardProto

}
