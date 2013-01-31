package de.tototec.sbuild.embedded

import java.io.File
import de.tototec.sbuild.Project
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

object SBuildEmbedded {
  private[embedded] def debug(msg: => String) = Console.println(msg)
  private[embedded] def error(msg: => String) = Console.println(msg)

}

class SBuildEmbedded(projectFile: File, sbuildHomeDir: File) {

  import SBuildEmbedded._

  lazy val project: Project = {

    val config = new Config()
    config.verbose = true
    config.buildfile = projectFile.getName

    val classpathConfig = new ClasspathConfig()
    classpathConfig.sbuildHomeDir = sbuildHomeDir
    classpathConfig.noFsc = true

    debug("About to read project: " + projectFile)
    val projectReader: ProjectReader = new SimpleProjectReader(config, classpathConfig)

    implicit val sbuildProject = new Project(projectFile, projectReader)
    config.defines.asScala foreach {
      case (key, value) => sbuildProject.addProperty(key, value)
    }

    debug("About to read SBuild project: " + projectFile);
    try {
      projectReader.readProject(sbuildProject, projectFile)
    } catch {
      case e: Throwable =>
        error("Could not read Project file. Cause: " + e.getMessage)
        throw e
    }

    sbuildProject
  }

  def exportedDependencies(exportName: String): ExportedDependenciesResolver =
    new ExportedDependenciesResolverImpl(project, exportName)

}

trait ExportedDependenciesResolver {

  def dependencies: Seq[String]
  def fileDependencies: Seq[File]
  def targetRefs: TargetRefs
  def depToFileMap: Map[String, Seq[File]]

  def resolve(dependency: String): Either[String, File]
  def resolve(dependency: File): Either[String, File]

  def resolveAll: Seq[Either[String, File]]

}

class ExportedDependenciesResolverImpl(project: Project, exportName: String) extends ExportedDependenciesResolver {

  import SBuildEmbedded._

  implicit val _project = project

  override val dependencies: Seq[String] = {
    val depsXmlString = project.properties.getOrElse(exportName, "<deps></deps>")
    debug("Determine Eclipse classpath by evaluating '" + exportName + "' to: " + depsXmlString)
    val depsXml = XML.loadString(depsXmlString)

    val deps: Seq[String] = (depsXml \ "dep") map {
      depXml => depXml.text
    }

    deps
  }

  override val fileDependencies: Seq[File] = {
    TargetRefs.fromSeq(dependencies.map(d => TargetRef(d))).files
  }

  override val targetRefs: TargetRefs = {
    TargetRefs.fromSeq(dependencies.map(d => TargetRef(d)))
  }

  override def depToFileMap: Map[String, Seq[File]] = dependencies.map { dep => (dep, TargetRef(dep).files) }.toMap

  override def resolve(dependency: File): Either[String, File] = resolve(dependency.getPath)

  override def resolve(dependency: String): Either[String, File] = {
    val resolveDep = Path(dependency)
    if (fileDependencies.find(d => d == resolveDep).isEmpty)
      return Left(s"""Cannot resolve file "${dependency}" as it is not an exported dependency.""")

    val targetRef = TargetRef(dependency)

    project.findTarget(targetRef) match {
      case Some(target) =>
        // we have a target for this, so we need to resolve it, when required
        try {
          SBuildRunner.preorderedDependenciesTree(curTarget = target)
          Right(resolveDep)
        } catch {
          case e: SBuildException =>
            Left("Could not resolve dependency: " + target)
        }
      case None =>
        targetRef.explicitProto match {
          case None | Some("file") =>
            // this is a file, so we need simply to add it to the classpath
            // but first, we check that it is absolute or if not, we make it absolute (based on their project)
            Right(resolveDep)
          case Some("phony") =>
            // This is a phony target, we will ignore it for now
            debug("Ignoring phony target: " + targetRef)
            Left("Ignoring phony target: " + targetRef)
          case _ =>
            // A scheme we might have a scheme handler for
            try {
              val target = project.createTarget(targetRef)
              try {
                SBuildRunner.preorderedDependenciesTree(curTarget = target)
                Right(resolveDep)
              } catch {
                case e: SBuildException =>
                  debug("Could not resolve dependency: " + target)
                  Left("Could not resolve dependency: " + target)
              }
            } catch {
              case e: SBuildException =>
                error("Could not resolve dependency: " + targetRef + ". Reason: " + e.getMessage)
                Left("Could not resolve dependency: " + targetRef + ". Reason: " + e.getMessage)
            }
        }
    }
  }

  override def resolveAll: Seq[Either[String, File]] = fileDependencies.map { d => resolve(d) }

}

