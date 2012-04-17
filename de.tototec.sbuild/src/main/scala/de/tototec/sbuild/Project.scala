package de.tototec.sbuild

import java.io.File

class Project(_projectFile: File, projectReader: ProjectReader, _projectPool: Option[ProjectPool]) {

  def this(projectFile: File, projectReader: ProjectReader) {
    this(projectFile, projectReader, None)
  }

  val projectFile: File = _projectFile.getAbsoluteFile.getCanonicalFile
  require(projectFile.exists)

  val projectDirectory: File = projectFile.getParentFile
  require(projectDirectory.exists)
  require(projectDirectory.isDirectory)

  private[sbuild] val projectPool = _projectPool match {
    case Some(p) => p
    case None => new ProjectPool(this)
  }

  private var _modules: List[Project] = List()
  def modules: List[Project] = _modules

  private[sbuild] def findOrCreateModule(dirOrFile: String): Project = {
    // various checks
    if (projectReader == null) {
      throw new SBuildException("Does not know how to read the sub project")
    }

    val newProjectDirOrFile = uniqueFile(dirOrFile)
    if (!newProjectDirOrFile.exists) {
      throw new ProjectConfigurationException("Subproject/module '" + dirOrFile + "' does not exists")
    }

    val newProjectFile = if (newProjectDirOrFile.isFile) {
      newProjectDirOrFile
    } else {
      new File(newProjectDirOrFile, "SBuild.scala")
    }

    if (!newProjectFile.exists) {
      throw new ProjectConfigurationException("Subproject/module '" + dirOrFile + "' does not exists")
    }

    // file exists checks passed, now check for double-added projects

    val projectAlreadyIncluded = projectPool.projects.find { p =>
      p.projectFile == newProjectFile
    }

    val module = projectAlreadyIncluded match {
      case Some(existing) => existing
      case _ =>
        val newProject = new Project(newProjectFile, projectReader, Some(projectPool))
        properties foreach {
          case (key, value) => newProject.addProperty(key, value)
        }

        projectReader.readProject(newProject, newProjectFile)

        projectPool.addProject(newProject)

        newProject
    }

    _modules = modules ::: List(module)

    module
  }

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

    val target: Target = new ProjectTarget(targetRef.name, file, phony, handler, this)
    targets += (file -> target)
    target
  }

  def findTarget(targetRef: TargetRef): Option[Target] = findTarget(targetRef, searchInAllProjects = false)

  def findTarget(targetRef: TargetRef, searchInAllProjects: Boolean): Option[Target] = {
    uniqueTargetFile(targetRef) match {
      case UniqueTargetFile(file, phony, handler) => targets.get(file) match {
        // If nothing was found and the target in question is a file target and searchInAllProjects was requested, then search in other projects
        case None if searchInAllProjects && !phony =>
          // search in other projects
          val allCandidates = projectPool.projects.map { otherProj =>
            if (otherProj == this) {
              None
            } else {
              otherProj.targets.get(file) match {
                // If the found one is phony, it is not a perfect match
                case Some(t) if t.phony => None
                case x => x
              }
            }
          }
          val candidates = allCandidates.filter(_.isDefined)
          candidates.size match {
            case 0 => None
            case 1 => candidates.head
            case x =>
              // Found more than one. What should we do about it? 
              // For now just fail
              throw new SBuildException("Found more than one match for dependency '" + file +
                " in all registered modules. Occurences:" +
                candidates.map { case Some(t) => "\n - " + t.name + " [" + t.project.projectFile + "]" }.mkString)
          }

        case x => x
      }
    }
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
      val absFile = new File(projectDirectory, fileName)
      absFile.getCanonicalFile
    }
  }

  def prerequisites(target: Target): List[Target] = prerequisites(target, searchInAllProjects = false)

  def prerequisites(target: Target, searchInAllProjects: Boolean): List[Target] = target.dependants.map { dep =>
    findTarget(dep, searchInAllProjects) match {
      case Some(target) => target
      case None =>
        // TODO: if none target was found, look in other project if they provide the target
        dep.explicitProto match {
          case Some("phony") =>
            throw new ProjectConfigurationException("Non-existing prerequisite '" + dep.name + "' found for target: " + target)
          case None | Some("file") =>
            // try to find a file
            createTarget(dep) exec {
              val file = Path(dep.name)(this)
              if (!file.exists || !file.isDirectory) {
                throw new ProjectConfigurationException("Don't know how to build prerequisite: " + dep)
              }
            }
          case _ =>
            // A scheme handler might be able to resolve this thing
            createTarget(dep)
        }
    }
  }.toList

  //  def prerequisitesMap: Map[Target, List[Target]] = targets.values.map(goal => (goal, prerequisites(goal))).toMap

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

class ProjectPool(project: Project) {
  private var _projects: Map[String, Project] = Map((project.projectFile.getPath -> project))

  def addProject(project: Project) {
    _projects += (project.projectFile.getPath -> project)
  }

  def projects: Seq[Project] = _projects.values.toSeq
}