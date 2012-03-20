package de.tototec.sbuild

import java.io.File
import java.net.URI
import java.net.MalformedURLException
import scala.collection

trait Target extends TargetRef {
  def filePath: String
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

class TargetImpl(val name: String, val filePath: String, val protocol: String, val handler: SchemeHandler) extends Target {

  private var _exec: () => Unit = _
  override def action = _exec
  private var help: String = _
  private var prereqs = Seq[TargetRef]()
  override def dependants = prereqs

  def dependsOn(goals: => Seq[TargetRef]): Target = {
    prereqs = goals
    TargetImpl.this
  }

  def exec(execution: => Unit): Target = {
    TargetImpl.this._exec = () =>
      {
        execution
      }
    TargetImpl.this
  }

  def help(help: String): Target = {
    TargetImpl.this.help = help
    TargetImpl.this
  }

  override def toString() = {
    def hasExec = _exec match {
      case null => "non"
      case _ => "defined"
    }
    "Target(" + name + "=>" + filePath + ", dependsOn=" + prereqs.map(g => g.name).mkString("[", ", ", "]") + ", exec=" + hasExec + ")"
  }

  override lazy val phony = filePath.startsWith("phony:")

  lazy val targetFile: Option[File] = if (filePath.startsWith("file:")) { Some(new File(filePath.substring("file:".length))) } else { None }

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
        val prereqs = project.prerequisites(TargetImpl.this)
        if (prereqs.exists(_.phony)) exit("Some dependencies are phony") else {
          if (prereqs.exists(goal => goal.targetFile.isEmpty || !goal.targetFile.get.exists)) exit("Some prerequisites does not exists") else {

            val fileLastModified = targetFile.get.lastModified
            verbose("Target file last modified: " + fileLastModified)

            val prereqsLastModified = prereqs.foldLeft(0: Long)((max, goal) => Math.max(max, goal.targetFile.get.lastModified))
            verbose("Prereqisites last modified: " + prereqsLastModified)

            if (fileLastModified < prereqsLastModified) exit("Prerequisites are newer") else true
          }
        }
      }
    }
  }
}

case class ParsedTargetName(val name: String, val protocol: String, val filePath: String, val schemeHandler: SchemeHandler) {
  lazy val isPhony = protocol == "phony"
}

object Target {

  // implicit def toTarget(name: String)(implicit project: Project): Target = Target(name)

  implicit def toGoalRef(name: String): TargetRef = new TargetRef {
    def name = name;
  }

  implicit def toSeq(goal: Target): Seq[Target] = Seq(goal)

  def parseTargetName(name: String): ParsedTargetName = {
    // split url and search handler
    name.split(":", 2).toList match {
      case "phony" :: namePart :: Nil => new ParsedTargetName(namePart, "phony", "phony:" + namePart, null)
      case "file" :: namePart :: Nil => new ParsedTargetName(namePart, "file", "file:" + new File(namePart).getAbsolutePath, null)
      case namePart :: Nil => new ParsedTargetName(name, "file", "file:" + new File(namePart).getAbsolutePath, null)
      case proto :: name :: Nil => schemeHandlers.get(proto) match {
        case Some(handler) => new ParsedTargetName(name, proto, handler.localPath(name), handler)
        case None => throw new SBuildException("Unsupported Protocol in: " + name)
      }
      case Nil => throw new SBuildException("Unsupported target name: " + name)
    }
  }

  //  implicit def implicitToSeq(symbol: Symbol): Seq[Goal] = Seq(implicitToGoal(symbol))
  //  implicit def implicitToGoal(symbol: Symbol): Goal = Goal(symbol.name)

  def apply(name: String)(implicit project: Project): Target = {

    project.findTarget(name) match {
      case Some(goal) => goal
      case None => {
        val ParsedTargetName(newName, proto, path, handler) = parseTargetName(name)
        val target = new TargetImpl(name, path, proto, handler)
        if (handler != null) {
          // default task is to resolve the goal target
          target.exec {
            import de.tototec.sbuild.runner.SBuild
            SBuild.verbose("Executing scheme handler (" + proto + ") resolver task: " + path)
            handler.resolve(newName) match {
              case None => SBuild.verbose("Executed scheme handler (" + proto + ") resolver task: " + path)
              case Some(x: SBuildException) => {
                SBuild.verbose("Scheme handler (" + proto + ") resolver task failed: " + path)
                throw x
              }
              case Some(x: Throwable) => {
                SBuild.verbose("Scheme handler (" + proto + ") resolver task failed: " + path)
                throw new SBuildException("Problems while finding Goal with name: " + name, x)
              }
            }

          }
        }
        project.targets = project.targets ++ target
        target
      }
    }
  }

  private var schemeHandlers = Map[String, SchemeHandler]()

  def registerSchemeHandler(scheme: String, handler: SchemeHandler) {
    schemeHandlers += ((scheme, handler))
  }

}

