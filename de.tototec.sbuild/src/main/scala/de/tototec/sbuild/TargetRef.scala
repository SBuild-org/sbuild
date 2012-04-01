package de.tototec.sbuild

import java.io.File

object TargetRef {

  implicit def fromTarget(target: Target): TargetRef = TargetRef(target)
  implicit def fromString(name: String): TargetRef = TargetRef(name)
  implicit def fromFile(file: File): TargetRef = TargetRef(file)

  def apply(name: String): TargetRef = new TargetRef(name)
  def apply(target: Target): TargetRef = new TargetRef(target.name)
  def apply(file: File): TargetRef = new TargetRef("file:" + file.getPath)

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

  override def toString = name

}

