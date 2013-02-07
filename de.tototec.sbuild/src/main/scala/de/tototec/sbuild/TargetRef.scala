package de.tototec.sbuild

import java.io.File

object TargetRef {

  implicit def fromTarget(target: Target): TargetRef = TargetRef(target)
  implicit def fromString(name: String)(implicit project: Project): TargetRef = TargetRef(name)
  implicit def fromFile(file: File)(implicit project: Project): TargetRef = TargetRef(file)

  def apply(name: String)(implicit project: Project): TargetRef = new TargetRef(name)
  def apply(target: Target): TargetRef = new TargetRef(target.name)(target.project)
  def apply(file: File)(implicit project: Project): TargetRef = new TargetRef("file:" + file.getPath)

}

class TargetRef(val ref: String)(implicit project: Project) {

  val (explicitProject: Option[File], name: String) = ref.split("::", 2) match {
    case Array(p, n) => (Some(Path(p)), n)
    case Array(n) => (None, n)
  }

  val explicitProto: Option[String] = name.split(":", 2) match {
    case Array(proto, name) => Some(proto)
    case Array(name) => None
  }

  val nameWithoutProto = name.split(":", 2) match {
    case Array(_, name) => name
    case _ => name
  }

  def nameWithoutStandardProto = name.split(":", 2) match {
    case Array(p, name) if p == "phony" || p == "file" => name
    case Array(_, _) => name
    case _ => name
  }

  override def toString = name

  protected[sbuild] def targetProject: Option[Project] =
    if (explicitProject == None || project.projectFile == explicitProject.get)
      Some(project)
    else
      project.findModule(explicitProject.get.getName)

  protected[sbuild] def safeTargetProject: Project =
    targetProject match {
      case Some(p) => p
      case _ =>
        val e = new TargetNotFoundException(s"""Project "${explicitProject.get}" not found.""")
        e.buildScript = Some(project.projectFile)
        throw e
    }

  /**
   * Get the files, this TargetRef is referencing or producing, if any.
   * Should only call from inside an execution block of a target.
   */
  def files: Seq[File] = {
    if (WithinTargetExecution.get == null) {
      throw InvalidApiUsageException.localized("'TargetRef.files' can only be used inside an exec block of a target.")
    }
    explicitProto match {
      case Some("phony") => Seq()
      case None | Some("file") => Seq(Path(nameWithoutProto)(safeTargetProject))
      case Some(scheme) => safeTargetProject.schemeHandlers.get(scheme) match {
        case Some(handler) => Seq(Path(TargetRef(handler.localPath(nameWithoutProto)).nameWithoutProto)(safeTargetProject))
        case _ =>
          val e = new ProjectConfigurationException(s"""No scheme handler registered for scheme "${scheme}".""")
          e.buildScript = Some(project.projectFile)
          throw e
      }
    }
  }

}

