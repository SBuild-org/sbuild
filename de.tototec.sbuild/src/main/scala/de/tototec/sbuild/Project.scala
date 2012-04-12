package de.tototec.sbuild

import scala.tools.nsc.io.Directory
import java.io.File

class Project(val projectDirectory: Directory) {

  //  private var targetRefs = List[TargetRef]()

  /**
   * Map(file -> Target) of targets.
   */
  private[sbuild] var targets: Map[File, Target] = Map()

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

  def prerequisites(target: Target): List[Target] = target.dependants.map { dep =>
    findTarget(dep) match {
      case Some(target) => target
      case None => dep.explicitProto match {
        case None | Some("phony") | Some("file") =>
          throw new ProjectConfigurationException("Non-existing prerequisite '" + dep.name + "' found for target: " + target)
        case _ =>
          // A scheme handler might be able to resolve this thing
          createTarget(dep)
      }
    }
  }.toList

  def prerequisitesMap: Map[Target, List[Target]] = targets.values.map(goal => (goal, prerequisites(goal))).toMap

  private var schemeHandlers: Map[String, SchemeHandler] = Map()

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

  private[sbuild] var antProject: Option[Any] = None

  //  def isTargetUpToDate(target: Target): Boolean = {
  //    lazy val prefix = "Target " + target.name + ": "
  //    def verbose(msg: => String) = Util.verbose(prefix + msg)
  //    def exit(cause: String): Boolean = {
  //      Util.verbose(prefix + "Not up-to-date: " + cause)
  //      false
  //    }
  //
  //    if (target.phony) exit("Target is phony") else {
  //      if (target.targetFile.isEmpty || !target.targetFile.get.exists) exit("Target file does not exists") else {
  //        val prereqs = prerequisites(target)
  //        if (prereqs.exists(_.phony)) exit("Some dependencies are phony") else {
  //          if (prereqs.exists(goal => goal.targetFile.isEmpty || !goal.targetFile.get.exists)) exit("Some prerequisites does not exists") else {
  //
  //            val fileLastModified = target.targetFile.get.lastModified
  //            verbose("Target file last modified: " + fileLastModified)
  //
  //            val prereqsLastModified = prereqs.foldLeft(0: Long)((max, goal) => math.max(max, goal.targetFile.get.lastModified))
  //            verbose("Prereqisites last modified: " + prereqsLastModified)
  //
  //            if (fileLastModified < prereqsLastModified) exit("Prerequisites are newer") else true
  //          }
  //        }
  //      }
  //    }
  //  }

  /**
   * Check if the target is up-to-date. This check will respect the up-to-date state of direct dependencies.
   */
  def isTargetUpToDate(target: Target, dependenciesWhichWereUpToDateStates: Map[Target, Boolean] = Map()): Boolean = {
    lazy val prefix = "Target " + target.name + ": "
    def verbose(msg: => String) = Util.verbose(prefix + msg)
    def exit(cause: String): Boolean = {
      Util.verbose(prefix + "Not up-to-date: " + cause)
      false
    }

    if (target.phony) exit("Target is phony") else {
      if (target.targetFile.isEmpty || !target.targetFile.get.exists) exit("Target file does not exists") else {
        val (phonyPrereqs, filePrereqs) = prerequisites(target).partition(_.phony)
        if (phonyPrereqs.exists(t => !dependenciesWhichWereUpToDateStates.getOrElse(t, false)))
          // phony targets can only be considered up-to-date, if they retrieved their up-to-date state themselves while beeing executed
          exit("Some dependencies are phony and were not up-to-date")
        else {
          if (filePrereqs.exists(t => t.targetFile.isEmpty || !t.targetFile.get.exists)) exit("Some prerequisites does not exists") else {

            val fileLastModified = target.targetFile.get.lastModified
            verbose("Target file last modified: " + fileLastModified)

            val prereqsLastModified = filePrereqs.foldLeft(0: Long)((max, goal) => math.max(max, goal.targetFile.get.lastModified))
            verbose("Prereqisites last modified: " + prereqsLastModified)

            if (fileLastModified < prereqsLastModified) exit("Prerequisites are newer") else true
          }
        }
      }
    }
  }

}

