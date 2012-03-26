package de.tototec.sbuild

import java.io.File
import java.net.URI
import java.net.MalformedURLException
import scala.collection

trait Target {
  def file: File
  def name: String
  //  def filePath: String
  //  def dependsOn(goal: TargetRef): Target
  def dependsOn(goals: => Seq[TargetRef]): Target
  def dependants: Seq[TargetRef]
  def exec(execution: => Unit): Target
  def action: () => Unit
  def help(help: String): Target
  def phony: Boolean
  //  /**
  //   * Default: use the file to which the target name resolves.
  //   * If pattern is specified, we produce files matching the given pattern.
  //   * If given "" as pattern, the goal is "phony", which means, the output cannot be checked by sbuild to actuality.
  //   */
  //  def produces(pattern: String): Target
  //  def needsToExec(needsToExec: => Boolean): Target
  def upToDate(implicit project: Project): Boolean
  def targetFile: Option[File]
}

object Target {
  def apply(targetRef: TargetRef)(implicit project: Project): Target = project.findOrCreateTarget(targetRef)
}

class ProjectTarget private[sbuild] (val name: String, val file: File, val phony: Boolean, handler: Option[SchemeHandler]) extends Target {

  private var _exec: () => Unit = handler match {
    case None => null
    case Some(handler) => () => {
      handler.resolve(new TargetRef(name).nameWithoutProto) match {
        case None =>
        case Some(t) => throw t
      }
    }
  }
  private var help: String = _
  private var prereqs = Seq[TargetRef]()

  override def action = _exec
  override def dependants = prereqs

  override def dependsOn(goals: => Seq[TargetRef]): Target = {
    prereqs = goals
    ProjectTarget.this
  }

  override def exec(execution: => Unit): Target = {
    _exec = () =>
      {
        execution
      }
    this
  }

  override def help(help: String): Target = {
    this.help = help
    this
  }

  override def toString() = {
    def hasExec = _exec match {
      case null => "non"
      case _ => "defined"
    }
    "Target(" + TargetRef(name).nameWithoutProto + "=>" + file + (if (phony) "[phony]" else "") + ", dependsOn=" + prereqs.map(t => t.name).mkString(",") + ", exec=" + hasExec + ")"
  }

  lazy val targetFile: Option[File] = phony match {
    case false => Some(file)
    case true => None
  }

  override def upToDate(implicit project: Project): Boolean = {
    lazy val prefix = "Target " + name + ": "
    def verbose(msg: => String) = Util.verbose(prefix + msg)
    def exit(cause: String): Boolean = {
      Util.verbose(prefix + "Not up-to-date: " + cause)
      false
    }

    //    verbose("check up-to-date")

    if (phony) exit("Target is phony") else {
      if (targetFile.isEmpty || !targetFile.get.exists) exit("Target file does not exists") else {
        val prereqs = project.prerequisites(ProjectTarget.this)
        if (prereqs.exists(_.phony)) exit("Some dependencies are phony") else {
          if (prereqs.exists(goal => goal.targetFile.isEmpty || !goal.targetFile.get.exists)) exit("Some prerequisites does not exists") else {

            val fileLastModified = targetFile.get.lastModified
            verbose("Target file last modified: " + fileLastModified)

            val prereqsLastModified = prereqs.foldLeft(0: Long)((max, goal) => math.max(max, goal.targetFile.get.lastModified))
            verbose("Prereqisites last modified: " + prereqsLastModified)

            if (fileLastModified < prereqsLastModified) exit("Prerequisites are newer") else true
          }
        }
      }
    }
  }

}
