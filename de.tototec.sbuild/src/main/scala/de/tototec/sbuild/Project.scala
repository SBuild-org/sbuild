package de.tototec.sbuild

import java.io.File

import scala.reflect.ClassTag

trait ProjectBase {
  def projectDirectory: File
  def projectFile: File
  protected[sbuild] def baseProject: Option[Project]
  // protected[sbuild] val log: SBuildLogger
  protected[sbuild] val monitor: CmdlineMonitor
  /**
   * Find an explicitly registered target.
   *
   * @param targetRef The target name to find.
   * @param searchInAllProjects If `true` and no target was found in
   *        the current project and the TargetRef did not contain a project
   *        referrer, search in all other projects.
   * @param Also find targets that were created/cached implicit in the project but do not have a corresponding explicit definition.
   */
  protected[sbuild] def findTarget(targetRef: TargetRef, searchInAllProjects: Boolean = false, includeImplicit: Boolean = false): Option[Target]
  protected[sbuild] def prerequisites(target: Target, searchInAllProjects: Boolean = false): Seq[Target]
  protected[sbuild] def prerequisitesGrouped(target: Target, searchInAllProjects: Boolean = false): Seq[Seq[Target]]
  protected[sbuild] def findModule(dirOrFile: String): Option[Project]
  protected[sbuild] def properties: Map[String, String]
  /** All active scheme handler in this project. */
  protected[sbuild] def schemeHandlers: Map[String, SchemeHandler]
  /** All explicit defined targets in this project. */
  protected[sbuild] def targets: Seq[Target]
  // since 0.4.0.9002
  /** Get the directory which contains the included source file containing the type T. */
  protected[sbuild] def includeDirOf[T: ClassTag]: File
}

trait MutableProject extends ProjectBase {
  def uniqueTargetFile(targetRef: TargetRef): UniqueTargetFile
  protected[sbuild] def addProperty(key: String, value: String)
  protected[sbuild] def registerSchemeHandler(scheme: String, handler: SchemeHandler)
  protected[sbuild] def replaceSchemeHandler(scheme: String, handler: SchemeHandler)
  protected[sbuild] def findOrCreateTarget(targetRef: TargetRef, isImplicit: Boolean = false): Target
  protected[sbuild] def createTarget(targetRef: TargetRef, isImplicit: Boolean = false): Target
  protected[sbuild] def findOrCreateModule(dirOrFile: String, copyProperties: Boolean): Project
  /** Very exerimental. Do not use yet. */
  protected[sbuild] def registerPlugin(plugin: ExperimentalPlugin)
  protected[sbuild] def applyPlugins

  /**
   * Determine the target associated to the given target reference.
   * @param targetRef The target reference.
   * If the target reference contains an explicit project qualifier, the target will be searched in the associated project.
   * @param searchInAllProjects If `true` and the target reference is a file but not found in the project, all projects will be searched for targets that produce that same file.
   * @param supportCamelCaseShortCuts If `true`, the target reference is handled as a camelCase pattern, which must match exactly one target.
   * @return `Some(Target)` if the target could be derived from the project configuration or `None` if the target is unknown.
   * @since 0.5.0.9002
   */
  protected[sbuild] def determineRequestedTarget(targetRef: TargetRef, searchInAllProjects: Boolean, supportCamelCaseShortCuts: Boolean): Option[Target]
}

trait ProjectAntSupport {
  protected[sbuild] var antProject: Option[Any]
}

trait Project extends MutableProject with ProjectAntSupport

case class UniqueTargetFile(file: File, phony: Boolean, handler: Option[SchemeHandler])

