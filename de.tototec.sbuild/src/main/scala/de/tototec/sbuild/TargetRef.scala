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

  /**
   * Get the files, this TargetRef is referencing or producing, if any.
   */
  def files: Seq[File] = project.findTarget(this, true) match {
    case Some(t) => t.targetFile match {
      case Some(f) => Seq(f)
      case _ => Seq()
    }
    case _ => Seq() 
  }
}

