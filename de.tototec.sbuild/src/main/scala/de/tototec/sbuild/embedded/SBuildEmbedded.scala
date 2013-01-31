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
import de.tototec.sbuild.Target

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

  def resolveToFile(dependency: String): Either[String, File]

  def resolveAllToFile: Seq[Either[String, File]]

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

  override def resolveToFile(dependency: String): Either[String, File] = {

    if (dependencies.find(d => d == dependency).isEmpty)
      return Left(s"""Cannot resolve dependency "${dependency}" as it is not an exported dependency.""")

    SBuildRunner.determineRequestedTarget(dependency) match {

      // No Target definition -> Check if a file with same name exists
      case Left(targetName) =>

        val targetRef = TargetRef(dependency)
        targetRef.explicitProto match {

          case None | Some("file") =>
            val file = targetRef.files.head
            if (file.exists)
              Right(file)
            else
              Left(s"""Cannot resolve dependency "${dependency}". Don't know how to make file "${file}".""")

          case Some("phony") =>
            return Left(s"""Will not resolve dependency "${dependency}" as it is a phony dependency.""")

          case _ =>
            return Left(s"""Could not found target for dependency "${dependency}".""")
        }

      // Found a target, now resolve it
      case Right(target) =>

        target.targetFile match {
          
          case None =>
            return Left(s"""Will not resolve dependency "${dependency}" as it is a phony dependency.""")

          case Some(file) =>
            try {
              SBuildRunner.preorderedDependenciesTree(curTarget = target)
              if (file.exists)
                Right(file)
              else
                Left(s"""Successfully resolved dependency "${dependency}" to file "${file}", but file (now) does not exists.""")
            } catch {
              case e: SBuildException =>
                debug("Could not resolve dependency: " + dependency)
                Left("Could not resolve dependency: " + dependency)
            }

        }

    }
  }

  override def resolveAllToFile: Seq[Either[String, File]] = dependencies.map { d => resolveToFile(d) }

}

