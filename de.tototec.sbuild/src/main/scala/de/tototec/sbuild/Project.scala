package de.tototec.sbuild

import java.io.File

class Project(_projectFile: File,
              _projectReader: ProjectReader = null,
              _projectPool: Option[ProjectPool] = None,
              val log: SBuildLogger = SBuildNoneLogger) {

  private val projectReader: Option[ProjectReader] = Option(_projectReader)

  val projectFile: File = Path.normalize(_projectFile)
  if (!projectFile.exists)
    throw new ProjectConfigurationException("Project file '" + projectFile + "' does not exists")

  val projectDirectory: File = projectFile.getParentFile
  require(projectDirectory.exists, "Project directory '" + projectDirectory + "' does not exists")
  require(projectDirectory.isDirectory, "Project directory '" + projectDirectory + "' is not an directory")

  private[sbuild] val projectPool = _projectPool match {
    case Some(p) => p
    case None => new ProjectPool(this)
  }

  private var _modules: List[Project] = List()
  def modules: List[Project] = _modules

  private[sbuild] def findModule(dirOrFile: String): Option[Project] = {
    Path(dirOrFile)(this) match {
      case x if !x.exists => None
      case newProjectDirOrFile =>
        val newProjectFile = newProjectDirOrFile match {
          case x if x.isFile => newProjectDirOrFile
          case x => new File(x, "SBuild.scala")
        }
        newProjectFile.exists match {
          case false => None
          case true =>
            projectPool.projects.find { p =>
              p.projectFile == newProjectFile
            }
        }
    }
  }

  private[sbuild] def findOrCreateModule(dirOrFile: String, copyProperties: Boolean = true): Project = {

    val newProjectDirOrFile = Path(dirOrFile)(this)
    if (!newProjectDirOrFile.exists) {
      val ex = new ProjectConfigurationException("Subproject/module '" + dirOrFile + "' does not exists")
      ex.buildScript = Some(this._projectFile)
      throw ex
    }

    val newProjectFile = if (newProjectDirOrFile.isFile) {
      newProjectDirOrFile
    } else {
      new File(newProjectDirOrFile, "SBuild.scala")
    }

    if (!newProjectFile.exists) {
      val ex = new ProjectConfigurationException("Subproject/module '" + dirOrFile + "' does not exists")
      ex.buildScript = Some(this._projectFile)
      throw ex
    }

    // file exists checks passed, now check for double-added projects

    val projectAlreadyIncluded = projectPool.projects.find { p =>
      p.projectFile == newProjectFile
    }

    val module = projectAlreadyIncluded match {
      case Some(existing) => existing
      case _ =>
        projectReader match {
          case None =>
            val ex = new SBuildException("Does not know how to read the sub project")
            ex.buildScript = Some(projectFile)
            throw ex

          case Some(reader) =>
            val newProject = new Project(newProjectFile, reader, Some(projectPool), log)

            // copy project properties 
            if (copyProperties) properties.foreach {
              case (key, value) => newProject.addProperty(key, value)
            }

            reader.readProject(newProject, newProjectFile)

            projectPool.addProject(newProject)

            newProject
        }
    }

    _modules = modules ::: List(module)

    module
  }

  /**
   * Map(file -> Target) of targets.
   */
  private[sbuild] var targets: Map[File, Target] = Map()

  def findOrCreateTarget(targetRef: TargetRef, isImplicit: Boolean = false): Target =
    findTarget(targetRef, searchInAllProjects = false) match {
      case Some(t: ProjectTarget) if t.isImplicit && !isImplicit =>
        // we change it to explicit
        t.isImplicit = false
        t
      case Some(t) => t
      case None => createTarget(targetRef, isImplicit = isImplicit)
    }

  def createTarget(targetRef: TargetRef, isImplicit: Boolean = false): Target = {
    if (explicitForeignProject(targetRef).isDefined) {
      throw new ProjectConfigurationException("Cannot create Target which explicitly references a different project in its name: " + targetRef)
    }

    val UniqueTargetFile(file, phony, handler) = uniqueTargetFile(targetRef)
    val proto = targetRef.explicitProto match {
      case Some(x) => x
      case None => "file"
    }

    val target = new ProjectTarget(targetRef.name, file, phony, handler, this)
    target.isImplicit = isImplicit
    targets += (file -> target)
    target
  }

  /**
   * @param targetRef The target name to find.
   * @param searchInAllProjects If <code>true</code and no target was found in
   *        the current project and the TargetRef did not contain a project
   *        referrer, search in all other projects.
   */
  def findTarget(targetRef: TargetRef, searchInAllProjects: Boolean = false): Option[Target] = {
    explicitForeignProject(targetRef) match {
      case Some(pFile) =>
        projectPool.propjectMap.get(pFile) match {
          case None => throw new TargetNotFoundException("Could not found target: " + targetRef + ". Unknown project: " + pFile)
          case Some(p) => p.findTarget(targetRef, searchInAllProjects = false)
        }
      case None =>
        uniqueTargetFile(targetRef) match {
          case UniqueTargetFile(file, phony, _) => targets.get(file) match {

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
                  // We check the maximal one with contains dependencies and/or actions
                  val realTargets = candidates.filter(t => t.get.action == null && t.get.dependants.isEmpty)
                  realTargets match {
                    case Seq(bestCandidate) => // Perfect, we will use that one
                      bestCandidate
                    case Seq() => // All targets are just placeholders for files, so we can take the first one
                      candidates.head
                    case _ => // More than one candidate have explicit action and/or dependencies, this is an conflict we cant solve automatically

                      // For now just fail
                      throw new SBuildException("Found more than one match for dependency '" + file +
                        " in all registered modules. Occurences:" +
                        candidates.map {
                          case Some(t) => "\n - " + t.name + " [" + t.project.projectFile + "]"
                          case _ => // to avoid compiler warning
                        }.mkString)
                  }
              }

            case x => x

          }
        }
    }
  }

  case class UniqueTargetFile(file: File, phony: Boolean, handler: Option[SchemeHandler])

  def explicitForeignProject(targetRef: TargetRef): Option[File] = {
    val ownerProject: File = targetRef.explicitProject match {
      case Some(p) => if (p.isDirectory) {
        new File(p, "SBuild.scala")
      } else p
      case None => projectFile
    }

    if (ownerProject != projectFile) {
      Some(ownerProject)
    } else {
      None
    }
  }

  def uniqueTargetFile(targetRef: TargetRef): UniqueTargetFile = {
    val foreignProject = explicitForeignProject(targetRef)

    // file of phony is: projectfile + "/" + targetRef.name
    // as projectfile is a file, 

    targetRef.explicitProto match {
      // case Some("phony") => UniqueTargetFile(new File(projectFile, targetRef.nameWithoutProto), true, None)
      case Some("phony") => UniqueTargetFile(Path(targetRef.nameWithoutProto)(this), true, None)
      case None | Some("file") => UniqueTargetFile(Path(targetRef.nameWithoutProto)(this), false, None)
      case Some(proto) if foreignProject.isDefined =>
        val e = new ProjectConfigurationException("Cannot handle custom scheme target reference '" + targetRef + "' of foreign projects.")
        e.buildScript = foreignProject match {
          case None => Some(projectFile)
          case x => x
        }
        throw e
      case Some(proto) => schemeHandlers.get(proto) match {
        case Some(handler) =>
          val handlerOutput = handler.localPath(targetRef.nameWithoutProto)
          val outputRef = new TargetRef(handlerOutput)(this)
          val phony = outputRef.explicitProto match {
            case Some("phony") => true
            case Some("file") => false
            case _ =>
              val e = new UnsupportedSchemeException("The defined scheme \"" + outputRef.explicitProto + "\" did not resolve to phony or file protocol.")
              e.buildScript = foreignProject match {
                case None => Some(projectFile)
                case x => x
              }
              throw e
          }
          UniqueTargetFile(Path(outputRef.nameWithoutProto)(this), phony, Some(handler))
        case None =>
          val e = new UnsupportedSchemeException("No scheme handler registered, that supports scheme: " + proto)
          e.buildScript = foreignProject match {
            case None => Some(projectFile)
            case x => x
          }
          throw e
      }
    }
  }

  def prerequisites(target: Target, searchInAllProjects: Boolean = false): List[Target] = target.dependants.map { dep =>
    findTarget(dep, searchInAllProjects = searchInAllProjects) match {
      case Some(target) => target
      case None =>
        // TODO: if none target was found, look in other project if they provide the target
        dep.explicitProto match {
          case Some("phony") =>
            throw new TargetNotFoundException("Non-existing prerequisite '" + dep.name + "' found for target: " + target)
          case None | Some("file") =>
            // try to find a file
            createTarget(dep, isImplicit = true) exec {
              val file = Path(dep.name)(this)
              if (!file.exists || !file.isDirectory) {
                val e = new ProjectConfigurationException("Don't know how to build prerequisite: " + dep)
                e.buildScript = explicitForeignProject(dep) match {
                  case None => Some(projectFile)
                  case x => x
                }
                throw e
              }
            }
          case _ =>
            // A scheme handler might be able to resolve this thing
            createTarget(dep, isImplicit = true)
        }
    }
  }.toList

  //  def prerequisitesMap: Map[Target, List[Target]] = targets.values.map(goal => (goal, prerequisites(goal))).toMap

  private[this] var _schemeHandlers: Map[String, SchemeHandler] = Map()
  private[sbuild] def schemeHandlers: Map[String, SchemeHandler] = _schemeHandlers
  private[this] def schemeHandlers_=(schemeHandlers: Map[String, SchemeHandler]) = _schemeHandlers = schemeHandlers

  def registerSchemeHandler(scheme: String, handler: SchemeHandler) {
    schemeHandlers.get(scheme).map {
      _ => log.log(LogLevel.Info, s"""Replacing scheme handler "${scheme}" for project "${projectFile}".""")
    }
    schemeHandlers += ((scheme, handler))
  }

  // Default Scheme Handler
  {
    implicit val p = this
    SchemeHandler("http", new HttpSchemeHandler())
    SchemeHandler("mvn", new MvnSchemeHandler())
    SchemeHandler("zip", new ZipSchemeHandler())
    SchemeHandler("scan", new ScanSchemeHandler())
  }

  private var _properties: Map[String, String] = Map()
  private[sbuild] def properties: Map[String, String] = _properties
  def addProperty(key: String, value: String) = if (_properties.contains(key)) {
    log.log(LogLevel.Debug, "Ignoring redefinition of property: " + key)
  } else {
    log.log(LogLevel.Debug, "Defining property: " + key + " with value: " + value)
    _properties += (key -> value)
  }

  private[sbuild] var antProject: Option[Any] = None

  override def toString: String =
    getClass.getSimpleName + "(" + projectFile + ",targets=" + targets.map { case (f, p) => p.name }.mkString(",") + ")"
}

class ProjectPool(project: Project) {
  private var _projects: Map[File, Project] = Map((project.projectFile -> project))

  def addProject(project: Project) {
    _projects += (project.projectFile -> project)
  }

  def projects: Seq[Project] = _projects.values.toSeq
  def propjectMap: Map[File, Project] = _projects
}