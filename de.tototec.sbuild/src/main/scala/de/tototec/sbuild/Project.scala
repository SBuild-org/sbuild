package de.tototec.sbuild

import scala.tools.nsc.io.Directory
import java.io.File
import org.apache.tools.ant.{ Project => AntProject }
import org.apache.tools.ant.ProjectComponent
import org.apache.tools.ant.BuildListener
import org.apache.tools.ant.BuildEvent

class Project(val projectDirectory: Directory) {

  //  private var targetRefs = List[TargetRef]()

  /**
   * List of targets. By convention, each target is stored with its protocol
   */
  private[sbuild] var targets: Map[File, Target] = Map()

  //  def findOrCreateTargetRef(name: String): TargetRef = {
  //    val candidate = new TargetRef(name)
  //    targetRefs.find { tr =>
  //      candidate.nameWithoutProto == tr.nameWithoutProto &&
  //        ((candidate.explicitProto.isDefined && candidate.explicitProto == tr.explicitProto) || candidate.explicitProto.isEmpty)
  //    } match {
  //      case Some(x) => x
  //      case _ =>
  //        targetRefs ::= candidate
  //        candidate
  //    }
  //  }

  def findOrCreateTarget(targetRef: TargetRef): Target = findTarget(targetRef) match {
    case Some(t) => t
    case None => createTarget(targetRef)
  }

  def createTarget(targetRef: TargetRef): Target = {
    val UniqueTargetFile(file, phony, handler) = uniqueTargetFile(targetRef)
    val proto = targetRef.explicitProto match {
      case Some(x) => x
      case None => "file"
    }

    val target: Target = new ProjectTarget(targetRef.name, file, phony, handler)
    targets += (file -> target)
    target
  }

  def findTarget(targetRef: TargetRef): Option[Target] =
    uniqueTargetFile(targetRef) match {
      case UniqueTargetFile(file, _, _) => targets.get(file)
    }

  case class UniqueTargetFile(file: File, phony: Boolean, handler: Option[SchemeHandler])

  def uniqueTargetFile(targetRef: TargetRef): UniqueTargetFile = targetRef.explicitProto match {
    case Some("phony") => UniqueTargetFile(uniqueFile(targetRef.nameWithoutProto), true, None)
    case None | Some("file") => UniqueTargetFile(uniqueFile(targetRef.nameWithoutProto), false, None)
    case Some(proto) => schemeHandlers.get(proto) match {
      case Some(handler) =>
        val handlerOutput = handler.localPath(targetRef.nameWithoutProto)
        val outputRef = new TargetRef(handlerOutput)
        val phony = outputRef.explicitProto match {
          case Some("phony") => true
          case Some("file") => false
          case _ => throw new UnsupportedSchemeException("The defined scheme \"" + outputRef.explicitProto + "\" did not resolve to phony or file protocol.")
        }
        UniqueTargetFile(uniqueFile(outputRef.nameWithoutProto), phony, Some(handler))
      case None => throw new UnsupportedSchemeException("No scheme handler registered, that supports scheme:" + proto)
    }
  }

  def uniqueFile(fileName: String): File = {
    val origFile = new File(fileName)
    if (origFile.isAbsolute) {
      origFile.getCanonicalFile
    } else {
      val absFile = new File(projectDirectory.jfile, fileName)
      absFile.getCanonicalFile
    }
  }

  def prerequisites(target: Target) = target.dependants.map { dep => findOrCreateTarget(dep)
    //    findTarget(dep) match {
    //      case Some(target) => target
    //      case None => throw new ProjectConfigurationException("Non-existing dependency '" + dep.name + "' found in goal: " + target)
    //    }
  }.toList

  def prerequisitesMap: Map[Target, List[Target]] = targets.values.map(goal => (goal, prerequisites(goal))).toMap

  private var schemeHandlers = Map[String, SchemeHandler]()

  def registerSchemeHandler(scheme: String, handler: SchemeHandler) {
    schemeHandlers += ((scheme, handler))
  }

  private var _properties: Map[String, String] = Map()
  def properties: Map[String, String] = _properties
  def addProperty(key: String, value: String) = if (_properties.contains(key)) {
    Util.verbose("Ignoring redefinition of property: " + key)
  } else {
    _properties += (key -> value)
  }
  
}

