package de.tototec.sbuild

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.util.Properties

import scala.collection.JavaConverters.asScalaSetConverter
import scala.reflect.ClassTag

import de.tototec.sbuild.SchemeHandler.SchemeContext
import de.tototec.sbuild.TargetRefs.fromSeq

class BuildFileProject(_projectFile: File,
                       _projectReader: ProjectReader = null,
                       _projectPool: Option[ProjectPool] = None,
                       typesToIncludedFilesProperties: Option[File] = None,
                       override val monitor: CmdlineMonitor = NoopCmdlineMonitor) extends Project {

  private[this] val log = Logger[BuildFileProject]

  private val projectReader: Option[ProjectReader] = Option(_projectReader)

  override val projectFile: File = Path.normalize(_projectFile)
  if (!projectFile.exists)
    throw new ProjectConfigurationException("Project file '" + projectFile + "' does not exists")

  override val projectDirectory: File = projectFile.getParentFile
  require(projectDirectory.exists, "Project directory '" + projectDirectory + "' does not exists")
  require(projectDirectory.isDirectory, "Project directory '" + projectDirectory + "' is not an directory")

  private[sbuild] val projectPool: ProjectPool = _projectPool match {
    case Some(p) => p
    case None => new ProjectPool(this)
  }

  override protected[sbuild] def baseProject: Option[Project] = projectPool.baseProject match {
    case p if p == this => None
    case p => Some(p)
  }

  private var _modules: List[Project] = Nil
  def modules: Seq[Project] = _modules.reverse

  override protected[sbuild] def findModule(dirOrFile: String): Option[Project] =
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

  override protected[sbuild] def findOrCreateModule(dirOrFile: String, copyProperties: Boolean): Project = {

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

    val projectAlreadyIncludedInProject = modules.find(p => p.projectFile == newProjectFile)
    projectAlreadyIncludedInProject.map { p =>
      monitor.warn(s"Module ${projectPool.formatProjectFileRelativeToBase(p)} is specified multiple times in project ${projectPool.formatProjectFileRelativeToBase(this)}")
    }

    val projectAlreadyIncludedInPool = projectPool.projects.find(p => p.projectFile == newProjectFile)
    synchronized {
      val module = projectAlreadyIncludedInPool match {
        case Some(existingInPool) => existingInPool
        case _ =>
          projectReader match {
            case None =>
              val ex = new SBuildException("Does not know how to read the sub project")
              ex.buildScript = Some(projectFile)
              throw ex

            case Some(reader) =>
              reader.readAndCreateProject(newProjectFile, properties, Some(projectPool), Some(monitor))
          }
      }

      _modules ::= module

      module
    }
  }

  /**
   * Map(file -> Target) of targets.
   * The explicit defined targets and implicitly created ones.
   */
  private var targetMap: Map[File, Target] = Map()
  override protected[sbuild] def targets: Seq[Target] = targetMap.values.toSeq.filter(!_.isImplicit)
  //  private def targets_=(targets: Map[File, Target]): Unit = _targets = targets

  override def findOrCreateTarget(targetRef: TargetRef, isImplicit: Boolean = false): Target =
    internalFindTarget(targetRef, searchInAllProjects = false, includeImplicit = true, supportCamelCaseShortCuts = false) match {
      case Some(t: ProjectTarget) if t.isImplicit && !isImplicit =>
        // we change it to explicit
        t.isImplicit = false
        t
      case Some(t) => t
      case None => createTarget(targetRef, isImplicit = isImplicit)
    }

  override def createTarget(targetRef: TargetRef, isImplicit: Boolean = false): Target = synchronized {
    explicitForeignProject(targetRef) match {
      case Some(pFile) if targetRef.explicitNonStandardProto.isDefined =>
        // This must be a scheme handler, and as we have both scheme and project defined, we WANT the target
        // See https://sbuild.tototec.de/sbuild/issues/112

        projectPool.propjectMap.get(pFile) match {
          case None =>
            val ex = new TargetNotFoundException("Could not find target: " + targetRef + ". Unknown project: " + pFile)
            ex.buildScript = Some(projectFile)
            throw ex
          case Some(p) =>
            p.createTarget(targetRef, isImplicit = true)
        }
      case Some(_) =>
        val ex = new ProjectConfigurationException("Cannot create Target which explicitly references a different project in its name: " + targetRef)
        ex.buildScript = Some(targetRef.safeTargetProject.projectFile)
        throw ex
      case None =>
        val UniqueTargetFile(file, phony, handler) = uniqueTargetFile(targetRef)
        val proto = targetRef.explicitProto match {
          case Some(x) => x
          case None => "file"
        }

        lazy val targetSchemeContext = SchemeContext(proto, targetRef.nameWithoutProto)

        val target = new ProjectTarget(targetRef.name, file, phony, this,
          initialPrereqs = handler match {
            case Some(handler: SchemeResolverWithDependencies) => handler.dependsOn(targetSchemeContext).targetRefs
            case _ => Seq[TargetRef]()
          },
          initialExec = handler match {
            case Some(handler: SchemeResolver) => { ctx: TargetContext => handler.resolve(targetSchemeContext, ctx) }
            case _ => null
          },
          initialTransparentExec = handler match {
            case Some(_: TransparentSchemeResolver) => true
            case _ => false
          },
          initialSideEffectFree = handler match {
            case Some(_: SideeffectFreeSchemeResolver) => true
            case _ => false
          })
        target.isImplicit = isImplicit
        targetMap += (file -> target)
        target
    }
  }

  /**
   * Find an explicitly registered target.
   *
   * @param targetRef The target name to find.
   * @param searchInAllProjects If <code>true</code> and no target was found in
   *        the current project and the TargetRef did not contain a project
   *        referrer, search in all other projects.
   * @param Also find targets that were created/cached implicit in the project but do not have a corresponding explicit definition.
   */
  override protected[sbuild] def findTarget(targetRef: TargetRef, searchInAllProjects: Boolean = false, includeImplicit: Boolean = false): Option[Target] =
    internalFindTarget(targetRef, searchInAllProjects, includeImplicit, supportCamelCaseShortCuts = false)

  /**
   * Find a target.
   *
   * @param targetRef The target name to find.
   * @param searchInAllProjects If `true` and no target was found in
   *        the current project and the TargetRef did not contain a project
   *        referrer, search in all other projects.
   * @param Also find targets that were created/cached implicit in the project but do not have a corresponding explicit definition.
   */
  private def internalFindTarget(targetRef: TargetRef,
                                 searchInAllProjects: Boolean,
                                 includeImplicit: Boolean,
                                 supportCamelCaseShortCuts: Boolean,
                                 originalRequestedProject: Project = this): Option[Target] =
    explicitForeignProject(targetRef) match {
      case Some(pFile) =>
        // delegate to the other project
        projectPool.propjectMap.get(pFile) match {
          case None =>
            val ex = new TargetNotFoundException("Could not find target: " + targetRef + ". Unknown project: " + pFile)
            ex.buildScript = Some(projectFile)
            throw ex
          case Some(p) => p.internalFindTarget(targetRef, searchInAllProjects = false, includeImplicit, supportCamelCaseShortCuts, originalRequestedProject = this) match {
            case None if targetRef.explicitNonStandardProto.isDefined =>
              None
            case None =>
              val ex = new TargetNotFoundException("Could not find target: " + targetRef + " in project: " + pFile)
              ex.buildScript = Some(projectFile)
              throw ex
            case x => x
          }
        }
      case None =>
        // handle in this project
        val localFound = uniqueTargetFile(targetRef) match {
          // the only case!
          case UniqueTargetFile(file, phony, _) => targetMap.get(file) match {

            case Some(found) if !found.isImplicit || includeImplicit => Some(found)

            // If nothing was found and the target in question is a file target and searchInAllProjects was requested, then search in other projects
            case None if searchInAllProjects && !phony =>
              // search in other projects
              val allCandidates = projectPool.projects.map { otherProj =>
                if (otherProj == this) {
                  None
                } else {
                  otherProj.targetMap.get(file) match {
                    // If the found one is phony, it is not a perfect match
                    case Some(t) if t.phony => None
                    case Some(t) if t.isImplicit && !includeImplicit => None
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
                  val realTargets = candidates.filter(t => t.get.action == null && t.get.dependants.targetRefs.isEmpty)
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

        // try to enhance the not-found result by looking for camelCase matches, if requested so
        localFound match {
          case None if supportCamelCaseShortCuts && Seq(None, Some("phony")).contains(targetRef.explicitProto) =>
            val matcher = new TargetNameMatcher(targetRef.name)
            val matches = targets.filter(t => t.phony && matcher.matches(TargetRef(t.name)(t.project).nameWithoutProto))
            matches match {
              case Seq() => None
              case Seq(foundTarget) =>
                monitor.info(CmdlineMonitor.Verbose, s"""Resolved shortcut camel case request "${targetRef}" to target "${foundTarget.formatRelativeTo(originalRequestedProject)}".""")
                Some(foundTarget)
              case multiMatch =>
                monitor.info(CmdlineMonitor.Verbose, s"""Ambiguous match for request "${targetRef}". Candidates: """ + multiMatch.map(_.formatRelativeTo(originalRequestedProject)).mkString(", "))
                // ambiguous match, found more that one
                // Todo: think about replace Option by Try, to communicate better reason why nothing was found
                None
            }
          case x => x
        }
    }

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

  override def uniqueTargetFile(targetRef: TargetRef): UniqueTargetFile = {
    def foreignProject = explicitForeignProject(targetRef)

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
        // here, we definitely have an project local target ref
        case Some(resolver: SchemeResolver) =>
          val schemeContext = SchemeContext(proto, targetRef.nameWithoutProto)
          val handlerOutput = resolver.localPath(schemeContext)
          val outputRef = new TargetRef(handlerOutput)(this)
          val phony = outputRef.explicitProto match {
            case Some("phony") => true
            case Some("file") => false
            case _ =>
              // A scheme resolver must resolve to "phony" or "file" only.
              val e = new UnsupportedSchemeException("The defined scheme \"" + outputRef.explicitProto + "\" did not resolve to phony or file protocol.")
              e.buildScript = Some(projectFile)
              throw e
          }
          UniqueTargetFile(Path(outputRef.nameWithoutProto)(this), phony, Some(resolver))
        case Some(handler: SchemeHandler) =>
          // This is a scheme handler but not a scheme resolver, which means, only the name gets aliased.
          // It might "resolve" to another scheme, so we have to evaluate the outcome of localPath recursive

          val schemeContext = SchemeContext(proto, targetRef.nameWithoutProto)
          val handlerOutput = handler.localPath(schemeContext)
          val expandedTargetRef = TargetRef(handlerOutput)(this)
          log.debug(s"""About to expand scheme handler "${proto}" from "${targetRef}" to "${expandedTargetRef}"""")

          // the recusive call!
          val uTF = uniqueTargetFile(expandedTargetRef)
          // if we are here, we had success to resolve the handler

          uTF.handler match {
            case Some(resolver: SchemeResolver) =>
              // the found scheme handler delegates to a "real" scheme resolver,
              // so instead of applying the bare handler we use a wrapper, which handles all possible implementable methods.

              // if the handler encapsulated an resolver, we have to wrap it here
              /**
               * Merges a primary SchemeHandler and a secondary SchemeResolver.
               * The localPath-method is delegated to the primary SchemeHandler.
               * All other methods will be delegated to the secondary SchemeResolver.
               */
              class WrappedSchemeResolver(primaryHandler: SchemeHandler, secondaryResolver: SchemeResolver)
                  extends SchemeResolver
                  with SchemeResolverWithDependencies {

                //                  def unwrappedPath(schemeContext: SchemeContext): String = handler.localPath(schemeContext).split(":", 2)(1)
                def unwrappedScheme(schemeContext: SchemeContext): SchemeContext =
                  primaryHandler.localPath(schemeContext).split(":", 2) match {
                    case Array(unwrappedProto, unwrappedPath) => SchemeContext(unwrappedProto, unwrappedPath)
                    // other cases do not apply, as we know there is an explicit scheme
                  }

                override def localPath(schemeContext: SchemeContext) = primaryHandler.localPath(schemeContext)

                override def resolve(schemeContext: SchemeContext, targetContext: TargetContext) = {
                  val unwrappedPath = this.unwrappedScheme(schemeContext)
                  log.debug(s"""About to resolve "${schemeContext}" by calling undelying scheme handler's resolve with path "${unwrappedPath}""")
                  secondaryResolver.resolve(unwrappedPath, targetContext)
                }

                override def dependsOn(schemeContext: SchemeContext): TargetRefs = TargetRefs.fromSeq(secondaryResolver match {
                  case withDeps: SchemeResolverWithDependencies => withDeps.dependsOn(unwrappedScheme(schemeContext)).targetRefs
                  case _ => Seq()
                })
              }
              UniqueTargetFile(uTF.file, uTF.phony, Some(new WrappedSchemeResolver(handler, resolver)))
            case _ =>
              // if the handler does not encapsulate a resolve, simple pass the inner UniqueTargetFile through
              uTF
          }
        case None =>
          val e = new UnsupportedSchemeException("No scheme handler registered, that supports scheme: " + proto)
          e.buildScript = Some(projectFile)
          throw e
      }
    }
  }

  override def prerequisites(target: Target, searchInAllProjects: Boolean = false): Seq[Target] =
    prerequisitesGrouped(target, searchInAllProjects).flatten

  // Since SBUild 0.5.0.9005
  override def prerequisitesGrouped(target: Target, searchInAllProjects: Boolean = false): Seq[Seq[Target]] =
    target.dependants.targetRefGroups.map { group =>
      group.map { dep =>
        internalFindTarget(dep, searchInAllProjects = searchInAllProjects, includeImplicit = true, supportCamelCaseShortCuts = false) match {
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
      }
    }

  private[this] var _schemeHandlers: Map[String, SchemeHandler] = Map()
  override protected[sbuild] def schemeHandlers: Map[String, SchemeHandler] = _schemeHandlers
  private[this] def schemeHandlers_=(schemeHandlers: Map[String, SchemeHandler]) = _schemeHandlers = schemeHandlers

  override def registerSchemeHandler(scheme: String, handler: SchemeHandler) {
    schemeHandlers.get(scheme).map {
      _ =>
        val msg = s"""Replacing scheme handler "${scheme}" for project "${projectFile}"."""
        log.info(msg)
        monitor.info(CmdlineMonitor.Default, msg)
    }
    schemeHandlers += ((scheme, handler))
  }
  override def replaceSchemeHandler(scheme: String, handler: SchemeHandler) {
    schemeHandlers.get(scheme).orElse {
      throw ProjectConfigurationException.localized(s"""Cannot replace scheme handler "${scheme}" for project "${projectFile}". No previous scheme handler registered under this name.""")
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

    // Experimental

    SchemeHandler("source", new MapperSchemeHandler(
      pathTranslators = Seq("mvn" -> { path => path + ";classifier=sources" })
    ))
    SchemeHandler("javadoc", new MapperSchemeHandler(
      pathTranslators = Seq("mvn" -> { path => path + ";classifier=javadoc" })
    ))

  }

  private var _properties: Map[String, String] = Map()
  override protected[sbuild] def properties: Map[String, String] = _properties
  override def addProperty(key: String, value: String) = if (_properties.contains(key)) {
    monitor.warn(CmdlineMonitor.Verbose, "Ignoring redefinition of property: " + key)
  } else {
    monitor.info(CmdlineMonitor.Verbose, "Defining property: " + key + " with value: " + value)
    _properties += (key -> value)
  }

  override protected[sbuild] var antProject: Option[Any] = None

  override def toString: String =
    getClass.getSimpleName + "(" + projectFile + ",targets=" + targets.map(_.name).mkString(",") + ")"

  private[this] var _pluginsToInitLater: Seq[ExperimentalPlugin] = Seq()

  override protected[sbuild] def registerPlugin(plugin: ExperimentalPlugin) {
    _pluginsToInitLater ++= Seq(plugin)
  }
  override protected[sbuild] def applyPlugins {
    while (!_pluginsToInitLater.isEmpty) {
      val plugin = _pluginsToInitLater.head
      log.debug(s"""About to initialize plugin "${plugin.getClass()}": ${plugin.toString()}""")
      plugin.init
      log.debug(s"""Initialized plugin "${plugin.getClass()}": ${plugin.toString()}""")
      _pluginsToInitLater = _pluginsToInitLater.tail
    }
  }

  // since 0.4.0.9002
  lazy val typesToIncludedFilesMap: Map[String, File] = typesToIncludedFilesProperties match {
    case Some(file) if file.exists() =>
      val props = new Properties()
      val inStream = new BufferedInputStream(new FileInputStream(file))
      try {
        import scala.collection.JavaConverters._
        props.load(inStream)
        props.entrySet().asScala.map(entry => (entry.getKey().asInstanceOf[String], new File(entry.getValue().asInstanceOf[String]))).toMap
      } catch {
        case e: Exception => Map()
      } finally {
        inStream.close()
      }

    case None => Map()
  }

  // since 0.4.0.9002
  def includeDirOf[T: ClassTag]: File = {
    val classTag = scala.reflect.classTag[T]
    val className = classTag.runtimeClass.getName()
    typesToIncludedFilesMap.get(className) match {
      case Some(file) if file.getParentFile() != null => file.getParentFile()
      case _ =>
        val ex = new ProjectConfigurationException(s"""Could not determine the location if the included file which contains class "${className}".""")
        ex.buildScript = Some(projectFile)
        throw ex
    }
  }

  override protected[sbuild] def determineRequestedTarget(targetRef: TargetRef, searchInAllProjects: Boolean, supportCamelCaseShortCuts: Boolean): Option[Target] =

    internalFindTarget(targetRef,
      searchInAllProjects = searchInAllProjects,
      includeImplicit = false,
      supportCamelCaseShortCuts = supportCamelCaseShortCuts,
      originalRequestedProject = this) match {
        case Some(target) => Some(target)
        case None => targetRef.explicitProto match {
          case None | Some("phony") | Some("file") => None
          case _ =>
            // A scheme handler might be able to resolve this thing
            Some(createTarget(targetRef, isImplicit = true))
        }
      }

}

class ProjectPool(val baseProject: BuildFileProject) {
  private var _projects: Map[File, BuildFileProject] = Map()
  addProject(baseProject)

  def addProject(project: BuildFileProject) {
    _projects += (project.projectFile -> project)
  }

  def projects: Seq[BuildFileProject] = _projects.values.toSeq
  def propjectMap: Map[File, BuildFileProject] = _projects

  def formatProjectFileRelativeToBase(project: Project): String =
    if (baseProject != project)
      baseProject.projectDirectory.toURI.relativize(project.projectFile.toURI).getPath
    else project.projectFile.getName

}
