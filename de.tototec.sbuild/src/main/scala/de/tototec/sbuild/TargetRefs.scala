package de.tototec.sbuild

import java.io.File

object TargetRefs {
  implicit def fromString(string: String)(implicit project: Project): TargetRefs = TargetRefs(TargetRef(string))
  implicit def fromFile(file: File)(implicit project: Project): TargetRefs = TargetRefs(TargetRef("file:" + file.getPath))
  implicit def fromTargetRef(targetRef: TargetRef): TargetRefs = TargetRefs(targetRef)
  implicit def fromTarget(target: Target): TargetRefs = TargetRefs(TargetRef(target.name)(target.project))
  implicit def fromSeq(targetRefs: Seq[TargetRef]): TargetRefs = TargetRefs(targetRefs: _*)
}

case class TargetRefs(val targetRefs: TargetRef*) {
  def ~(targetRefs: TargetRefs): TargetRefs = TargetRefs((this.targetRefs ++ targetRefs.targetRefs): _*)
  def ~(targetRef: TargetRef): TargetRefs = TargetRefs((targetRefs ++ Seq(targetRef)): _*)
  def ~(file: File)(implicit project: Project): TargetRefs = TargetRefs((targetRefs ++ Seq(TargetRef(file))): _*)
  def ~(string: String)(implicit project: Project): TargetRefs = TargetRefs((targetRefs ++ Seq(TargetRef(string))): _*)

  override def toString: String = targetRefs.map { _.name }.mkString(" ~ ")
}
