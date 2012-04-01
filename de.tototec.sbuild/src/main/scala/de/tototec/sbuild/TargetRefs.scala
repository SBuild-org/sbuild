package de.tototec.sbuild

import java.io.File

object TargetRefs {
  implicit def fromString(string: String): TargetRefs = TargetRefs(TargetRef(string))
  implicit def fromFile(file: File): TargetRefs = TargetRefs(TargetRef("file:" + file.getPath))
  implicit def fromTargetRef(targetRef: TargetRef): TargetRefs = TargetRefs(targetRef)
  implicit def fromTarget(target: Target): TargetRefs = TargetRefs(TargetRef(target.name))
  implicit def fromSeq(targetRefs: Seq[TargetRef]): TargetRefs = TargetRefs(targetRefs: _*)
}

case class TargetRefs(val targetRefs: TargetRef*) {
  def /(targetRefs: TargetRefs): TargetRefs = TargetRefs((this.targetRefs ++ targetRefs.targetRefs): _*)
  def /(targetRef: TargetRef): TargetRefs = TargetRefs((targetRefs ++ Seq(targetRef)): _*)
  def /(file: File): TargetRefs = TargetRefs((targetRefs ++ Seq(TargetRef(file))): _*)
  def /(string: String): TargetRefs = TargetRefs((targetRefs ++ Seq(TargetRef(string))): _*)

  override def toString: String = targetRefs.map { _.name }.mkString(" / ")
}
