package de.tototec.sbuild.runner

import java.io.File
import java.util.Date
import scala.collection.JavaConversions._
import de.tototec.cmdoption.CmdlineParser
import de.tototec.sbuild._
import java.util.UUID

object SBuildRunner {

  val version = "0.0.1"

  private[runner] var verbose = false

  def main(args: Array[String]) {
    val bootstrapStart = System.currentTimeMillis

    val config = new Config()
    val classpathConfig = new ClasspathConfig()
    val cp = new CmdlineParser(config, classpathConfig)
    cp.parse(args: _*)

    SBuildRunner.verbose = config.verbose

    if (config.showVersion) {
      println("SBuild " + version + " (c) 2011, 2012, ToToTec GbR, Tobias Roeser")
    }

    if (config.help) {
      cp.usage
      System.exit(0)
    }

    val sbuildClasspath: Array[String] = classpathConfig.sbuildClasspath match {
      case null => Array()
      case x => x.split(":")
    }
    val compileClasspath: Array[String] = classpathConfig.compileClasspath match {
      case null => Array()
      case x => x.split(":")
    }
    val projectClasspath: Array[String] = classpathConfig.projectClasspath match {
      case null => Array()
      case x => x.split(":")
    }

    val projectReader: ProjectReader = new ProjectReader() {
      override def readProject(projectToRead: Project, projectFile: File) {
        val script = new ProjectScript(projectFile, sbuildClasspath, compileClasspath, projectClasspath)
        if (config.clean) {
          script.clean
        }
        //  Compile Script and load compiled class
        val scriptInstance = script.compileAndExecute(projectToRead)
      }
    }

    val projectFile = new File(config.buildfile)
    val project = new Project(projectFile, projectReader)
    config.defines foreach {
      case (key, value) => project.addProperty(key, value)
    }

    projectReader.readProject(project, projectFile)

    verbose("Targets: \n" + project.targets.values.mkString("\n"))

    if (config.listTargets) {
      Console.println(project.targets.values.map { t =>
        TargetRef(t.name).nameWithoutProto + " \t" + (t.help match {
          case null => ""
          case s: String => s
        })
      }.mkString("\n"))
      System.exit(0)
    }

    // Targets requested from cmdline
    val targets = determineRequestedTargets(config.params)(project).toList

    // Execution plan
    val chain = preorderedDependencies(targets, skipExec = true)(project)

    {
      var line = 0
      verbose({
        val needProjectSuffix = chain.map { execT => execT.target.project.projectFile }.distinct.size > 1
        def projectName(execT: ExecutedTarget): String = if (!needProjectSuffix) "" else {
          execT.target.project match {
            case p if p == project => ""
            case otherProject => " [" + project.projectDirectory.toURI.relativize(otherProject.projectFile.toURI).getPath + "]"
          }
        }

        "Execution plan: \n" + chain.map { execT =>
          line += 1
          "  " + line + ". " + execT.target.name + projectName(execT)
        }.mkString("\n")
      })
    }

    val executionStart = System.currentTimeMillis
    val bootstrapTime = executionStart - bootstrapStart

    verbose("Executing...")
    preorderedDependencies(targets, execState = Some(new ExecState(maxCount = chain.size)))(project)
    if (!targets.isEmpty) {
      println("[100%] Execution finished. SBuild init time: " + bootstrapTime +
        " msec, Execution time: " + (System.currentTimeMillis - executionStart) + " msec")
    }

    verbose("Finished")
  }

  def determineRequestedTargets(targets: Seq[String])(implicit project: Project): Seq[Target] = {

    // The compile will throw a warning here, but we want this so
    val (requested: Seq[Target], invalid: Seq[String]) = targets.map { t =>
      project.findTarget(t, searchInAllProjects = false) match {
        case Some(target) => target
        case None => TargetRef(t).explicitProto match {
          case None | Some("phony") | Some("file") => t
          case _ =>
            // A scheme handler might be able to resolve this thing
            project.createTarget(TargetRef(t))
        }
      }
    }.partition(_.isInstanceOf[Target])

    if (!invalid.isEmpty) {
      throw new InvalidCommandlineException("Invalid target" + (if (invalid.size > 1) "s" else "") + " requested: " + invalid.mkString(", "));
    }

    requested
  }

  class ExecutedTarget(val target: Target, val needsExec: Boolean, val requestId: Option[String])

  class ExecState(var maxCount: Int, var currentNr: Int = 1)

  def preorderedDependencies(request: List[Target],
                             rootRequest: Option[Target] = None,
                             execState: Option[ExecState] = None,
                             skipExec: Boolean = false,
                             requestId: Option[String] = None,
                             dependencyTrace: List[Target] = List())(implicit project: Project): Array[ExecutedTarget] = {
    request match {
      case Nil => Array()
      case node :: tail =>

        // detect collisions

        def verboseTrackDeps(msg: => String) {
          //          this.verbose(List.fill(dependencyTrace.size + 1)(" |").mkString + msg)
          this.verbose(msg + {
            if (dependencyTrace.isEmpty) "" else "  <-- as dep of: " + dependencyTrace.map { _.name }.mkString(" <- ")
          })
        }

        val root = rootRequest match {
          case Some(root) =>
            if (root == node) {
              throw new RuntimeException("Cycles in dependency chain detected for: " + root)
            }
            root
          case None => node
        }

        // build prerequisites map

        val alreadyRun: Array[ExecutedTarget] = {

          val skipOrUpToDate = skipExec || project.isTargetUpToDate(node)
          // Execute prerequisites
          verbose("determine dependencies of: " + node.name)
          val dependencies = node.project.prerequisites(node, searchInAllProjects = true)
          verbose("dependencies of: " + node.name + " => " + dependencies.map(_.name).mkString(", "))

          // All direct dependencies share the same request id.
          // Later we can identify them and check, if they were up-to-date.
          val resolveDirectDepsRequestId = Some(UUID.randomUUID.toString)

          val executed = preorderedDependencies(dependencies.toList, Some(root),
            execState = execState,
            skipExec = skipOrUpToDate,
            requestId = resolveDirectDepsRequestId,
            dependencyTrace = node :: dependencyTrace)

          val doContextChecks = true

          verboseTrackDeps("Evaluating up-to-date state of: " + node.name)

          val execPhonyUpToDateOrSkip = skipOrUpToDate match {
            case true => true // already known up-to-date
            case false => if (!doContextChecks) false else {
              // Evaluate up-to-date state based on the list of executed tasks

              val directDepsExecuted = executed.filter(_.requestId == resolveDirectDepsRequestId)
              val targetWhichWereUpToDateStates: Map[Target, Boolean] =
                directDepsExecuted.toList.groupBy(e => e.target).map {
                  case (t, execs) => (t -> execs.forall(!_.needsExec))
                }

              //              // Imagine the case were the same 
              //              // dependencies was added twice to the direct dependencies. Both would be associated by the same target,
              //              // so the up-to-date state of the first executed dependency would always be used for all same
              //              // dependencies. Because of this, we must aggregate all running state of one target, even if that means 
              //              // we miss some skip-able targets  
              //              val targetWhichWereUpToDateStates: Map[Target, Boolean] =
              //                project.prerequisites(node).toList.distinct.map { t =>
              //                  executed.filter(e => e.target == t) match {
              //                    case Array() => (t -> false)
              //                    case xs => xs.find(e => e.needsExec) match {
              //                      case Some(_) => (t -> false)
              //                      case None => (t -> true)
              //                    }
              //                  }
              //                }.toMap

              project.isTargetUpToDate(node, targetWhichWereUpToDateStates)
            }
          }
          if (!skipOrUpToDate && execPhonyUpToDateOrSkip) {
            verbose("All executed phony dependencies of '" + node.name + "' were up-to-date.")
          }

          // Print State
          execState map { state =>
            val percent = (state.currentNr, state.maxCount) match {
              case (c, m) if (c > 0 && m > 0) =>
                val p = (c - 1) * 100 / m
                "[" + math.min(100, math.max(0, p)) + "%]"
              case (c, m) => "[" + c + "/" + m + "]"
            }

            def formatTarget(target: Target) = {
              target.project match {
                case p if p == project => "'" + target.name + "'"
                case otherProject => "'" + target.name + "' [" + project.projectDirectory.toURI.relativize(otherProject.projectFile.toURI).getPath + "]"
              }
            }

            if (execPhonyUpToDateOrSkip) {
              verbose(percent + " Skipping target " + formatTarget(node))
            } else {
              println(percent + " Executing target " + formatTarget(node))
            }
            state.currentNr += 1
          }

          val ctx = new TargetContext(node)

          if (execPhonyUpToDateOrSkip) {
            ctx.targetWasUpToDate = true
          } else {
            // Need to execute
            node.action match {
              case null => verbose("Nothing to execute for target: " + node.name)
              case exec =>
                try {
                  verbose("Executing target: " + node.name)
                  ctx.start
                  exec.apply(ctx)
                  ctx.end
                  verbose("Executed target: " + node.name + " in " + ctx.execDurationMSec + " msec")
                } catch {
                  case e: Throwable => {
                    ctx.end
                    verbose("Execution of target " + node.name + " aborted after " + ctx.execDurationMSec + " msec with errors: " + e.getMessage);
                    throw e
                  }
                }
            }
          }

          executed ++ Array(
            new ExecutedTarget(
              target = node,
              needsExec = !skipOrUpToDate,
              requestId = requestId
            )
          )
        }

        alreadyRun ++ preorderedDependencies(tail,
          execState = execState,
          skipExec = skipExec,
          dependencyTrace = dependencyTrace)
    }
  }

  def verbose(msg: => String) {
    if (verbose) println(msg)
  }

}