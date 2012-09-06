package de.tototec.sbuild.eclipse.plugin

import org.eclipse.core.runtime.IPath
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.IClasspathContainer
import org.eclipse.jdt.core.IClasspathEntry
import java.io.File
import de.tototec.sbuild.runner.Config
import de.tototec.sbuild.runner.ClasspathConfig
import de.tototec.sbuild.runner.SimpleProjectReader
import de.tototec.sbuild.ProjectReader
import de.tototec.sbuild.Project
import scala.collection.JavaConversions._
import scala.xml.XML
import scala.xml.factory.XMLLoader
import de.tototec.sbuild.runner.SBuildRunner
import de.tototec.sbuild.Target
import de.tototec.sbuild.SBuildException
import org.eclipse.jdt.core.JavaCore
import org.eclipse.core.runtime.Path
import de.tototec.sbuild.TargetRef
import de.tototec.sbuild.SBuildVersion
import org.eclipse.core.resources.ResourcesPlugin
import de.tototec.sbuild.TargetRefs

object SBuildClasspathContainer {
  val ContainerName = "de.tototec.sbuild.SBUILD_DEPENDENCIES"
  def SBuildHomeVariableName = "SBUILD_HOME"
  def classpathConfig(sbuildHomeDir: File) = new ClasspathConfig {
    _sbuildClasspath = sbuildHomeDir.getAbsolutePath + "/lib/de.tototec.sbuild-" + SBuildVersion.version + ".jar"
    _compileClasspath = sbuildHomeDir.getAbsolutePath + "/lib/scala-compiler-2.9.2.jar"
    _projectClasspath = sbuildHomeDir.getAbsolutePath + "/lib/de.tototec.sbuild.ant-" + SBuildVersion.version + ".jar"
  }
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

  protected var classpathEntries: Option[Array[IClasspathEntry]] = None

  def readProject {
    val config = new Config()
    config.verbose = true
    config.buildfile = settings.sbuildFile
    
    val buildFile = new File(projectRootFile, config.buildfile)

    // Skip if nothing is todo
    if (!this.classpathEntries.isEmpty && buildFile.lastModified == sbuildFileTimestamp) return

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
    val projectScript = try {
      projectReader.readProject(sbuildProject, buildFile)
    } catch {
      case e: Throwable =>
        debug("Could not read Project file. Cause: " + e.getMessage)
        throw e
    }

    val depsXmlString = sbuildProject.properties.getOrElse(settings.exportedClasspath, "<deps></deps>")
    debug("Determine Eclipse classpath by evaluating '" + settings.exportedClasspath + "' to: " + depsXmlString)
    val depsXml = XML.loadString(depsXmlString)

    val depsFromXml: Seq[String] = (depsXml \ "dep") map {
      depXml => depXml.text
    }

    val targetRefsFromScript: Option[TargetRefs] = if (depsFromXml.isEmpty) {
      // try to access a val/def with name eclipseClasspath
      try {
        val scriptWithCp = projectScript.asInstanceOf[{ def eclipseClasspath: TargetRefs }]
        Some(scriptWithCp.eclipseClasspath)
      } catch {
        case e: Exception =>
          debug("Could not found or access property 'eclipseClasspath': " + e.getMessage)
          None
      }
    } else None

    val deps = if (targetRefsFromScript.isDefined) {
      val refs = targetRefsFromScript.get.targetRefs.map(_.ref)
      debug("Read targetRefs from script property 'eclipseClasspath': " + refs)
      refs
    } else depsFromXml

    // TODO: for now, we silently drop invalid targets

    val (requestedTargets: Seq[Target], invalid: Seq[String]) = SBuildRunner.determineRequestedTargets(deps)
    // invalid means, there are no known targets or scheme handlers to produce that target, 
    // but chances are, that those targets are files that already exists
    val (fileTargetRefs, unsupportedTargetRefs) = invalid.partition { t =>
      TargetRef(t).explicitProto match {
        case None | Some("file") => true
        case _ => false
      }
    }

    if (!unsupportedTargetRefs.isEmpty)
      debug("Some targets are invalid and will not be added to the classpath: " + unsupportedTargetRefs)

    val classpathTargets: Seq[Target] = requestedTargets.distinct filter (_.targetFile.isDefined)

    val nonTargetClasspathFiles: Seq[File] = fileTargetRefs.map { ft =>
      new File(ft) match {
        case f if f.isAbsolute => f
        case _ => TargetRef(ft).explicitProject match {
          case None => new File(projectRootFile, ft)
          case Some(projFile: File) if projFile.isFile => new File(projFile.getParentFile, ft)
          case Some(projDir: File) => new File(projDir, ft)
        }
      }
    }

    // TODO: we first assemble the classpath, we will resolve it later (TODO!)

    val classpathFiles: Seq[File] = (classpathTargets map (_.targetFile.get)) ++ nonTargetClasspathFiles

    val classpathEntries = classpathFiles map { f =>
      debug("About to add classpath entry: " + f)
      val file = if (f.isAbsolute) f else f.getAbsoluteFile
      JavaCore.newLibraryEntry(new Path(f.getCanonicalPath), null /*sourcepath*/ , null)
    }

    this.sbuildFileTimestamp = buildFile.lastModified
    this.classpathEntries = Some(classpathEntries.toArray)

    // start resolve 
    classpathTargets.foreach { target =>
      try {
        debug("About to resolve dependency: " + target)
        SBuildRunner.preorderedDependencies(request = List(target))
      } catch {
        case e: SBuildException => debug("Could not resolve dependency: " + target)
      }
    }
  }

  override def getClasspathEntries: Array[IClasspathEntry] = {
    readProject
    classpathEntries.get
  }

}