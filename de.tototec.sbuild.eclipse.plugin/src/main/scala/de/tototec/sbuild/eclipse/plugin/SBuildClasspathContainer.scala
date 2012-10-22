package de.tototec.sbuild.eclipse.plugin

import java.io.File

import scala.collection.JavaConversions._
import scala.xml.XML
import scala.xml.factory.XMLLoader

import org.eclipse.core.resources.ProjectScope
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.Path
import org.eclipse.jdt.core.IJavaModel
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.IClasspathContainer
import org.eclipse.jdt.core.IClasspathEntry
import org.eclipse.jdt.core.JavaCore

import de.tototec.sbuild.Project
import de.tototec.sbuild.ProjectReader
import de.tototec.sbuild.SBuildException
import de.tototec.sbuild.SBuildVersion
import de.tototec.sbuild.Target
import de.tototec.sbuild.TargetRef
import de.tototec.sbuild.TargetRefs
import de.tototec.sbuild.runner.ClasspathConfig
import de.tototec.sbuild.runner.Config
import de.tototec.sbuild.runner.SBuildRunner
import de.tototec.sbuild.runner.SimpleProjectReader

// import org.eclipse.core.runtime.preferences.IScopeContext
// import org.osgi.service.prefs.Preferences

object SBuildClasspathContainer {
  val ContainerName = "de.tototec.sbuild.SBUILD_DEPENDENCIES"
  def SBuildHomeVariableName = "SBUILD_HOME"
  def classpathConfig(sbuildHomeDir: File) = {
    val config = new ClasspathConfig()
    config.sbuildHomeDir = sbuildHomeDir
    config
  }
  def SBuildPreferencesNode = "de.tototec.sbuild.eclipse.plugin"
  def WorkspaceProjectAliasNode = "workspaceProjectAlias"
}

/**
 * A ClasspathContainer, that will read a SBuild build file and read a special marked classpath available in Eclipse.
 *
 * TODO: check of file changes of buildfile
 * TODO: resolve the classpath in background
 *
 */
class SBuildClasspathContainer(path: IPath, private val project: IJavaProject) extends IClasspathContainer {

  override val getKind = IClasspathContainer.K_APPLICATION
  override val getDescription = "SBuild Libraries"
  override val getPath = path

  protected def projectRootFile: File = project.getProject.getLocation.makeAbsolute.toFile

  protected val settings: Settings = new Settings(path)

  protected var sbuildFileTimestamp: Long = 0
  protected var resolveActions: Option[Seq[ResolveAction]] = None

  def readWorkspaceProjectAliases: Map[String, String] = {
    val projectScope = new ProjectScope(project.getProject)
    projectScope.getNode(SBuildClasspathContainer.SBuildPreferencesNode) match {
      case null =>
        debug("Could not access prefs node: " + SBuildClasspathContainer.SBuildPreferencesNode)
        Map()
      case prefs =>
        prefs.node(SBuildClasspathContainer.WorkspaceProjectAliasNode) match {
          case null =>
            debug("Could not access prefs node: " + SBuildClasspathContainer.WorkspaceProjectAliasNode)
            Map()
          case prefs =>
            val keys = prefs.keys
            debug("Found aliases in prefs for the following dependencies: " + keys.mkString(", "))
            keys.map {
              name => (name -> prefs.get(name, ""))
            }.filter {
              case (key, value) => value != ""
            }.toMap
        }
    }
  }

  def readProject {
    val config = new Config()
    config.verbose = true
    config.buildfile = settings.sbuildFile

    val buildFile = new File(projectRootFile, config.buildfile)

    if (this.resolveActions.isDefined && buildFile.lastModified() == this.sbuildFileTimestamp) {
      // already read the project
      return
    }

    val sbuildHomePath: IPath = JavaCore.getClasspathVariable(SBuildClasspathContainer.SBuildHomeVariableName)
    if (sbuildHomePath == null) {
      throw new RuntimeException("Classpath variable 'SBUILD_HOME' not defined")
    }
    val sbuildHomeDir = sbuildHomePath.toFile
    debug("Trying to use SBuild " + SBuildVersion.version + " installed at: " + sbuildHomeDir)
    val classpathConfig = SBuildClasspathContainer.classpathConfig(sbuildHomeDir)

    debug("About to read project")
    val projectReader: ProjectReader = new SimpleProjectReader(config, classpathConfig)

    implicit val sbuildProject = new Project(buildFile, projectReader)
    config.defines foreach {
      case (key, value) => sbuildProject.addProperty(key, value)
    }

    debug("About to read SBuild project: " + buildFile);
    try {
      projectReader.readProject(sbuildProject, buildFile)
    } catch {
      case e: Throwable =>
        debug("Could not read Project file. Cause: " + e.getMessage)
        throw e
    }

    val depsXmlString = sbuildProject.properties.getOrElse(settings.exportedClasspath, "<deps></deps>")
    debug("Determine Eclipse classpath by evaluating '" + settings.exportedClasspath + "' to: " + depsXmlString)
    val depsXml = XML.loadString(depsXmlString)

    val deps: Seq[String] = (depsXml \ "dep") map {
      depXml => depXml.text
    }

    var resolveActions = Seq[ResolveAction]()

    val depsAsTargetRefs = deps.map(TargetRef(_))

    depsAsTargetRefs.foreach { targetRef =>

      sbuildProject.findTarget(targetRef) match {
        case Some(target) =>
          // we have a target for this, so we need to resolve it, when required
          def action: Boolean = try {
            SBuildRunner.preorderedDependencies(request = List(target))
            true
          } catch {
            case e: SBuildException =>
              debug("Could not resolve dependency: " + target)
              false
          }
          resolveActions = resolveActions ++ Seq(ResolveAction(target.file.getPath, targetRef.ref, action _))
        case None =>
          targetRef.explicitProto match {
            case None | Some("file") =>
              // this is a file, so we need simply to add it to the classpath
              // but first, we check that it is absolute or if not, we make it absolute (based on their project)
              val file = new File(targetRef.name) match {
                case f if f.isAbsolute => f
                case _ => targetRef.explicitProject match {
                  case None => new File(projectRootFile, targetRef.name)
                  case Some(projFile: File) if projFile.isFile => new File(projFile.getParentFile, targetRef.name)
                  case Some(projDir: File) => new File(projDir, targetRef.name)
                }
              }
              resolveActions = resolveActions ++ Seq(ResolveAction(file.getPath, targetRef.ref, file.exists _))
            case Some("phony") =>
              // This is a phony target, we will ignore it for now
              debug("Ignoring phony target: " + targetRef)
            case _ =>
              // A scheme we might have a scheme handler for
              try {
                val target = sbuildProject.createTarget(targetRef)
                def action: Boolean = try {
                  SBuildRunner.preorderedDependencies(request = List(target))
                  true
                } catch {
                  case e: SBuildException =>
                    debug("Could not resolve dependency: " + target)
                    false
                }
                resolveActions = resolveActions ++ Seq(ResolveAction(target.file.getPath, targetRef.ref, action _))

              } catch {
                case e: SBuildException => debug("Could not resolve dependency: " + targetRef + ". Reason: " + e.getMessage)
              }
          }
      }
    }

    this.resolveActions = Some(resolveActions)
    this.sbuildFileTimestamp = buildFile.lastModified
  }

  case class ResolveAction(result: String, name: String, action: () => Boolean)

  def calcClasspath: Seq[IClasspathEntry] = {
    readProject

    // Now, we have a Seq of ResolveActions.
    // We will now check, if some of these targets can be resolved from workspace.
    // - If so, we instead add an classpath entry with the existing and open workspace project
    // - Else, we resolve the depenency and add the result to the classpath

    def resolveViaSBuild(action: ResolveAction): IClasspathEntry = {
      val file = new File(action.result) match {
        case f if f.isAbsolute => f
        case f => f.getAbsoluteFile
      }
      if (settings.relaxedFetchOfDependencies && file.exists) {
        debug("Skipping resolve of already existing dependency: " + action.name)
      } else {
        debug("About to resolve dependency: " + action.name)
        val success = action.action()
        debug("Resolve successful: " + success)
      }
      debug("About to add classpath entry: " + action.result)
      JavaCore.newLibraryEntry(new Path(file.getCanonicalPath), null /*sourcepath*/ , null)
    }

    val javaModel: IJavaModel = JavaCore.create(project.getProject.getWorkspace.getRoot)

    val aliasesMap = readWorkspaceProjectAliases
    debug("Using workspaceProjectAliases: " + aliasesMap)
    val classpathEntries = resolveActions.get.map { action: ResolveAction =>
      aliasesMap.contains(action.name) match {
        case false => resolveViaSBuild(action)
        case true =>
          javaModel.getJavaProject(aliasesMap(action.name)) match {
            case javaProject if javaProject.exists =>
              debug("Using Workspace Project as alias for project: " + action.name)
              debug("About to add project entry: " + javaProject.getPath)
              JavaCore.newProjectEntry(javaProject.getPath)
            case _ => resolveViaSBuild(action)
          }
      }
    }

    classpathEntries
  }

  override def getClasspathEntries: Array[IClasspathEntry] = try {
    calcClasspath.toArray
  } catch {
    case e: Exception =>
      debug("Could not calculate classpath entries.", e)
      Array()
  }

}
