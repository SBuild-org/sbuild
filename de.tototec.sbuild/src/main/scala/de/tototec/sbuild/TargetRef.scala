package de.tototec.sbuild

object TargetRef {

  implicit def fromTarget(target: Target): TargetRef = TargetRef(target.name)
  implicit def fromTargetToSeq(target: Target): Seq[TargetRef] = Seq(TargetRef(target.name))
  implicit def fromString(name: String): TargetRef = TargetRef(name)
  implicit def fromStringToSeq(name: String): Seq[TargetRef] = Seq(TargetRef(name))
  implicit def toSeq(targetRef: TargetRef): Seq[TargetRef] = Seq(targetRef)

  def apply(name: String): TargetRef = new TargetRef(name)
}

class TargetRef(val name: String) {
  val explicitProto: Option[String] = name.split(":", 2) match {
    case Array(proto, name) => Some(proto)
    case Array(name) => None
  }
  val nameWithoutProto = name.split(":", 2) match {
    case Array(_, name) => name
    case _ => name
  }
}

