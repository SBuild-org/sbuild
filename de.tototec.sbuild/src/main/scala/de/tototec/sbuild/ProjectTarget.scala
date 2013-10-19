package de.tototec.sbuild

import java.io.File

class ProjectTarget private[sbuild] (override val name: String,
                                     override val file: File,
                                     override val phony: Boolean,
                                     override val project: Project,
                                     initialPrereqs: TargetRefs = TargetRefs(),
                                     initialExec: TargetContext => Any = null,
                                     initialTransparentExec: Boolean = false,
                                     initialSideEffectFree: Boolean = false) extends Target {

  private[sbuild] var isImplicit = false

  private var _help: String = _
  private var prereqs: TargetRefs = initialPrereqs

  private[this] var _transparentExec: Boolean = initialTransparentExec
  override private[sbuild] def isTransparentExec: Boolean = _transparentExec

  private[this] var _sideeffectFree: Boolean = initialSideEffectFree
  override private[sbuild] def isSideeffectFree: Boolean = _sideeffectFree

  private[this] var execReplaced: Boolean = false
  private[this] var _exec: TargetContext => Any = initialExec

  private[sbuild] override def action: TargetContext => Any = _exec
  private[sbuild] override def dependants: TargetRefs = prereqs

  override def dependsOn(targetRefs: TargetRefs): Target = {
    prereqs = prereqs ~ targetRefs
    ProjectTarget.this
  }

  override def exec(execution: => Any): Target = exec((_: TargetContext) => execution)
  override def exec(execution: TargetContext => Any): Target = {
    if (_exec != null) {
      project.monitor.warn(CmdlineMonitor.Default, s"Reassignment of exec block for target ${name} in project file ${project.projectFile}")
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
      "(" + name + "=>" + formatRelativeToBaseProject + (if (phony) "[phony]" else "") +
      ",dependsOn=" + prereqs +
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

  override def formatRelativeTo(baseProject: Project): String =
    (if (project != baseProject) {
      baseProject.projectDirectory.toURI.relativize(project.projectFile.toURI).getPath + "::"
    } else "") + TargetRef(this).nameWithoutStandardProto

  override def formatRelativeToBaseProject: String = formatRelativeTo(project.baseProject.getOrElse(project))

}
