package org.sbuild

import java.io.File

import org.sbuild.internal.I18n
import org.sbuild.internal.WithinTargetExecution

object TargetRef {

  implicit def fromTarget(target: Target)(implicit project: Project): TargetRef = TargetRef(target)
  implicit def fromString(name: String)(implicit project: Project): TargetRef = TargetRef(name)
  implicit def fromFile(file: File)(implicit project: Project): TargetRef = TargetRef(file)

  def apply(name: String)(implicit project: Project): TargetRef = new TargetRef(name)
  def apply(target: Target)(implicit project: Project): TargetRef = {
    val name = if (project == target.project) {
      target.name
    } else {
      s"${target.project.projectFile.getAbsolutePath()}::${target.name}"
    }
    new TargetRef(name)(project)
  }
  def apply(file: File)(implicit project: Project): TargetRef = new TargetRef("file:" + file.getPath)

}

class TargetRef(val ref: String)(implicit _project: Project) {

  private[this] def log = Logger[TargetRef]
  private val project = _project

  val (explicitProject: Option[File], name: String) = ref.split("::", 2) match {
    case Array(p, n) => (Some(Path(p)), n)
    case Array(n) => (None, n)
  }

  val explicitProto: Option[String] = name.split(":", 2) match {
    case Array(proto, name) => Some(proto)
    case Array(name) => None
  }

  def explicitNonStandardProto: Option[String] = explicitProto match {
    case Some(p) if p != "file" && p != "phony" => Some(p)
    case _ => None
  }

  val nameWithoutProto: String = name.split(":", 2) match {
    case Array(_, name) => name
    case _ => name
  }

  def nameWithoutStandardProto: String = name.split(":", 2) match {
    case Array(p, name) if p == "phony" || p == "file" => name
    case Array(_, _) => name
    case _ => name
  }

  def formatRelativeToProject: String = (name.split(":", 2) match {
    case Array("file", name) => Right(name)
    case Array("phony", name) => Left(name)
    case Array(_, _) => Left(name)
    case _ => Right(name)
  }) match {
    case Right(name) => new RichFile(Path(name)(project)).pathRelativeTo(project.projectDirectory) match {
      case Some(relative) if relative.isEmpty() => name
      case Some(relative) => relative
      case None => name
    }
    case Left(name) => name
  }

  override def toString: String = ref

  protected[sbuild] def targetProject: Option[Project] =
    if (explicitProject == None || project.projectFile == explicitProject.get)
      Some(project)
    else
      project.findModule(explicitProject.get.getPath)

  protected[sbuild] def safeTargetProject: Project =
    targetProject match {
      case Some(p) => p
      case _ =>
        val e = new TargetNotFoundException(s"""Project "${explicitProject.get}" not found.""")
        e.buildScript = Some(project.projectFile)
        throw e
    }

  /**
   * Get the files, this TargetRef is referencing or producing, if any.
   * Should only call from inside an execution block of a target.
   */
  def files: Seq[File] = {
    WithinTargetExecution.safeWithinTargetExecution("TargetRef.files", Some(project)) {
      withinTargetExec =>
        // Find the TargetContext of the already executed dependency, that matches this TargetRef

        // as all dependencies were already executed,
        // they all should have an associated Target instance, which we can search for.
        // So, we must find a target for the used TargetRef. 
        // When not, the used TargetRef was not part of the dependencies

        project.findTarget(this, searchInAllProjects = true, includeImplicit = true) match {
          case None =>
            // No target found, so this TargetRef can not be part of the dependencies 
            val msg = I18n.marktr("'TargetRef.files' is used for dependency \"{0}\", that is not declared with 'dependsOn'.")
            val ex = new ProjectConfigurationException(I18n.notr(msg, this.toString), null, I18n[TargetRef].tr(msg, this.toString))
            ex.buildScript = Some(project.projectFile)
            ex.targetName = Some(withinTargetExec.targetContext.name)
            throw ex

          case Some(referencedTarget) =>
            // search the associated TargetContext for that target

            withinTargetExec.directDepsTargetContexts.find { ctx => ctx.target == referencedTarget } match {
              case None =>
                // No target context found, so this TargetRef can not be part of the dependencies 
                log.debug("referencedTarget = " + referencedTarget + "\ndirectDepsTargetContexts = " + withinTargetExec.directDepsTargetContexts.mkString(",\n  "))
                val msg = I18n.marktr("'TargetRef.files' is used for dependency \"{0}\", that is not declared with 'dependsOn'.")
                val ex = new ProjectConfigurationException(I18n.notr(msg, this.toString), null, I18n[TargetRef].tr(msg, this.toString))
                ex.buildScript = Some(project.projectFile)
                ex.targetName = Some(withinTargetExec.targetContext.name)
                throw ex

              case Some(foundDepCtx) =>
                foundDepCtx.targetFiles
            }
        }
    }
  }

  override def hashCode(): Int = 41 * ref.hashCode() + project.hashCode()
  override def equals(other: Any) = other match {
    case that: TargetRef => that.canEqual(this) && this.ref == that.ref && this.project == that.project
    case _ => false
  }
  def canEqual(other: Any) = other.isInstanceOf[TargetRef]

}

