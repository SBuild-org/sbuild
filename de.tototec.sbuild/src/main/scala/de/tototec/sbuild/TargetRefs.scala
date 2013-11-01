package de.tototec.sbuild

import java.io.File

object TargetRefs extends TargetRefsImplicits {

  def apply(targetRefs: TargetRefs): TargetRefs = targetRefs
  def apply(targetRefs: TargetRef*): TargetRefs = new TargetRefs(Seq(targetRefs))

  implicit def fromString(string: String)(implicit project: Project): TargetRefs = TargetRefs.toTargetRefs_fromString(string)
  implicit def fromFile(file: File)(implicit project: Project): TargetRefs = TargetRefs.toTargetRefs_fromFile(file)
  implicit def fromTargetRef(targetRef: TargetRef): TargetRefs = TargetRefs.toTargetRefs_fromTargetRef(targetRef)
  implicit def fromTarget(target: Target): TargetRefs = TargetRefs.toTargetRefs_fromTarget(target)
  implicit def fromSeq(targetRefs: Seq[TargetRef]): TargetRefs = TargetRefs.toTargetRefs_fromSeq(targetRefs)

}

class TargetRefs private (val targetRefGroups: Seq[Seq[TargetRef]]) {

  lazy val targetRefs = targetRefGroups.flatten

  private[this] def isMulti: Boolean = targetRefGroups.size > 1

  private[this] def closedGroups: Seq[Seq[TargetRef]] = targetRefGroups.size match {
    case 1 => Seq()
    case n => targetRefGroups.take(n - 1)
  }

  private[this] def openGroup: Seq[TargetRef] = targetRefGroups.last

  private[this] def removeEmptyGroups(groups: Seq[Seq[TargetRef]]): Seq[Seq[TargetRef]] = groups.filter(!_.isEmpty)

  def ~(targetRefs: TargetRefs): TargetRefs =
    new TargetRefs(removeEmptyGroups(
      closedGroups ++
        Seq(openGroup ++ targetRefs.targetRefGroups.head) ++
        targetRefs.targetRefGroups.tail
    ))
  def ~(targetRef: TargetRef): TargetRefs = this.~(TargetRefs.fromTargetRef(targetRef))
  def ~(file: File)(implicit project: Project): TargetRefs = this.~(TargetRefs.fromFile(file))
  def ~(string: String)(implicit project: Project): TargetRefs = this.~(TargetRefs.fromString(string))
  def ~(target: Target): TargetRefs = this.~(TargetRefs.fromTarget(target))

  // since SBuild 0.5.0.9004
  def ~~(targetRefs: TargetRefs): TargetRefs =
    new TargetRefs(removeEmptyGroups(
      targetRefGroups ++
        Seq(targetRefs.targetRefGroups.head) ++
        targetRefs.targetRefGroups.tail
    ))
  def ~~(targetRef: TargetRef): TargetRefs = ~~(TargetRefs.fromTargetRef(targetRef))
  def ~~(file: File)(implicit project: Project): TargetRefs = ~~(TargetRefs.fromFile(file))
  def ~~(string: String)(implicit project: Project): TargetRefs = ~~(TargetRefs.fromString(string))
  def ~~(target: Target): TargetRefs = ~~(TargetRefs.fromTarget(target))

  override def toString: String = targetRefGroups.map { _.mkString(" ~ ") }.mkString(" ~~ ")

  /**
   * Get the files, this TargetRefs is referencing or producing, if any.
   */
  def files: Seq[File] = {
    WithinTargetExecution.get match {
      case null =>
        val ex = InvalidApiUsageException.localized("'TargetRefs.files' can only be used inside an exec block of a target.")
        throw ex
      case _ =>
        targetRefs.flatMap(tr => tr.files)
    }
  }

  def filesRelativeTo(baseDir: File): Seq[String] = {
    WithinTargetExecution.get match {
      case null =>
        val ex = InvalidApiUsageException.localized("'TargetRefs.filesRelativeTo' can only be used inside an exec block of a target.")
        throw ex
      case _ =>
        targetRefs.flatMap(tr => tr.filesRelativeTo(baseDir))
    }
  }

}

trait TargetRefsImplicits {
  implicit def toTargetRefs_fromString(string: String)(implicit project: Project): TargetRefs = TargetRefs(TargetRef(string))
  implicit def toTargetRefs_fromFile(file: File)(implicit project: Project): TargetRefs = TargetRefs(TargetRef(file))
  implicit def toTargetRefs_fromTargetRef(targetRef: TargetRef): TargetRefs = TargetRefs(targetRef)
  implicit def toTargetRefs_fromTarget(target: Target): TargetRefs = TargetRefs(TargetRef(target.name)(target.project))
  implicit def toTargetRefs_fromSeq(targetRefs: Seq[TargetRef]): TargetRefs = TargetRefs(targetRefs: _*)
}
