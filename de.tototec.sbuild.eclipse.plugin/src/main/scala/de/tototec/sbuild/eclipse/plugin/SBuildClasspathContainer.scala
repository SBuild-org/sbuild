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

object SBuildClasspathContainer {
  val ContainerName = "de.tototec.sbuild.SBUILD_DEPENDENCIES"
  def SBuildHomeVariableName = "SBUILD_HOME"
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

  def readProject {
    val sbuildHomePath: IPath = JavaCore.getClasspathVariable(SBuildClasspathContainer.SBuildHomeVariableName)
    if (sbuildHomePath == null) {
      throw new RuntimeException("Classpath variable 'SBUILD_HOME' not defined")
    }
    val sbuildHomeDir = sbuildHomePath.toFile

    val reader = SBuildClasspathProjectReader.load(sbuildHomeDir, settings, projectRootFile)

    val buildFile = reader.buildFile

    if (this.resolveActions.isDefined && buildFile.lastModified() == this.sbuildFileTimestamp) {
      // already read the project
      debug("Already read the project")
      return
    }

    this.resolveActions = Some(reader.readResolveActions)
    this.sbuildFileTimestamp = buildFile.lastModified()
  }

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

    val aliasesMap = WorkspaceProjectAliases.read(project)
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
