package de.tototec.sbuild.embedded

import java.io.File
import de.tototec.sbuild.Project
import de.tototec.sbuild.BuildFileProject
import de.tototec.sbuild.TargetRef
import de.tototec.sbuild.TargetRefs
import de.tototec.sbuild.runner.Config
import de.tototec.sbuild.runner.ClasspathConfig
import de.tototec.sbuild.runner.SimpleProjectReader
import de.tototec.sbuild.ProjectReader
import scala.collection.JavaConverters._
import scala.xml.XML
import de.tototec.sbuild.Path
import de.tototec.sbuild.runner.SBuildRunner
import de.tototec.sbuild.SBuildException
import de.tototec.sbuild.Target
import java.util.Properties
import de.tototec.sbuild.ProjectConfigurationException
import de.tototec.sbuild.TargetNotFoundException
import scala.util.Try
import scala.util.Failure

object SBuildEmbedded {
  private[embedded] def debug(msg: => String) = Console.println(msg)
  private[embedded] def error(msg: => String) = Console.println(msg)
}

class SBuildEmbedded(sbuildHomeDir: File) {

  import SBuildEmbedded._

  private[this] lazy val projectReader: ProjectReader = {
    val classpathConfig = new ClasspathConfig()
    classpathConfig.sbuildHomeDir = sbuildHomeDir
    classpathConfig.noFsc = true
    new SimpleProjectReader(classpathConfig)
  }

  // TODO: Method to check, if a project is up-to-date

  def loadProject(projectFile: File, props: Properties): Project = {

    try {
      debug("About to read SBuild project: " + projectFile)
      projectReader.readAndCreateProject(projectFile, props.asScala.toMap, None, None)
    } catch {
      case e: Throwable =>
        error("Could not read Project file. Cause: " + e.getMessage)
        throw e
    }

  }

  /**
   * Return the lastModified time stamp of the project and its dependencies.
   */
  def lastModified(projectFile: File): Long = {
    0L
  }

  def loadResolver(projectFile: File, props: Properties): EmbeddedResolver =
    new ProjectEmbeddedResolver(loadProject(projectFile, props))

}

trait EmbeddedResolver {

  def exportedDependencies(exportName: String): Seq[String]

  /**
   * Resolve the given dependency.
   */
  def resolve(dep: String, progressMonitor: ProgressMonitor): Try[Seq[File]]
}

class ProjectEmbeddedResolver(project: Project) extends EmbeddedResolver {

  override def exportedDependencies(exportName: String): Seq[String] = {
    val depsXmlString = project.properties.getOrElse(exportName, "<deps></deps>")
    SBuildEmbedded.debug("Determine Eclipse classpath by evaluating '" + exportName + "' to: " + depsXmlString)
    val depsXml = XML.loadString(depsXmlString)
    (depsXml \ "dep") map { _.text }
  }

  /**
   * Resolve the given dependency. Dependency to files, which do not have a target definition, but which exists, are considered as resolved.
   */
  override def resolve(dep: String, progressMonitor: ProgressMonitor): Try[Seq[File]] = Try(doResolve(dep, progressMonitor))

  protected def doResolve(dep: String, progressMonitor: ProgressMonitor): Seq[File] = {
    implicit val p = project

    SBuildRunner.determineRequestedTarget(dep, true) match {

      case None =>
        // not found
        // if an existing file, then proceed.
        val targetRef = TargetRef.fromString(dep)
        targetRef.explicitProto match {
          case None | Some("file") if targetRef.explicitProject == None && Path(targetRef.nameWithoutProto).exists =>
            Seq(Path(targetRef.nameWithoutProto))
          case _ =>
            throw new TargetNotFoundException(s"""Could not found target with name "${dep}" in project ${project.projectFile}.""")
        }

      case Some(target) =>

        // TODO: progress
        val executedTarget = SBuildRunner.preorderedDependenciesTree(curTarget = target)
        executedTarget.targetContext.targetFiles
    }
  }

}

