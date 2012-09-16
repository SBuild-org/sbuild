package de.tototec.sbuild.runner

import java.io.File
import java.util.Date
import scala.collection.JavaConversions._
import de.tototec.cmdoption.CmdlineParser
import de.tototec.sbuild._
import java.util.UUID
import java.io.FileOutputStream
import java.io.BufferedOutputStream
import java.io.PrintStream
import java.lang.reflect.InvocationTargetException
import de.tototec.cmdoption.CmdOption

object SBuildRunner {

  private[runner] var verbose = false

  def main(args: Array[String]) {
    val bootstrapStart = System.currentTimeMillis

    val config = new Config()
    val classpathConfig = new ClasspathConfig()
    val cmdlineConfig = new {
      @CmdOption(names = Array("--version"), description = "Show SBuild version.")
      var showVersion = false

      @CmdOption(names = Array("--help", "-h"), isHelp = true, description = "Show this help screen.")
      var help = false
    }
    val cp = new CmdlineParser(config, classpathConfig, cmdlineConfig)
    cp.parse(args: _*)

    if (cmdlineConfig.showVersion) {
      println("SBuild " + SBuildVersion.version + " (c) 2011, 2012, ToToTec GbR, Tobias Roeser")
    }

    if (cmdlineConfig.help) {
      cp.usage
      System.exit(0)
    }

    try {
      run(config = config, classpathConfig = classpathConfig, bootstrapStart = bootstrapStart)
    } catch {
      case e: ProjectConfigurationException =>
        Console.err.println("\n!!! SBuild detected an failure in the project configuration or the build scripts.")
        if (e.buildScript.isDefined) Console.err.println("!!! Project: " + e.buildScript.get)
        Console.err.println("!!! Message: " + e.getMessage)
        if (verbose) throw e
        System.exit(1)
      case e: SBuildException =>
        Console.err.println("\n!!! SBuild failed with an exception (" + e.getClass.getSimpleName + ").")
        if (e.isInstanceOf[BuildScriptAware] && e.asInstanceOf[BuildScriptAware].buildScript.isDefined)
          Console.err.println("!!! Project: " + e.asInstanceOf[BuildScriptAware].buildScript.get)
        Console.err.println("!!! Message: " + e.getMessage)
        if (verbose) throw e
        System.exit(1)
    }
  }

  def run(config: Config, classpathConfig: ClasspathConfig, bootstrapStart: Long = System.currentTimeMillis) {

    SBuildRunner.verbose = config.verbose

    val projectFile = new File(config.buildfile)

    if (config.createStub) {
      if (projectFile.exists) {
        throw new SBuildException("File '" + config.buildfile + "' already exists. ")
      }

      val className = projectFile.getName.substring(0, projectFile.getName.length - 6)
      val sbuildStub = """import de.tototec.sbuild._
import de.tototec.sbuild.TargetRefs._
import de.tototec.sbuild.ant._
import de.tototec.sbuild.ant.tasks._

@version(""" + "\"" + SBuildVersion.osgiVersion + "\"" + """)
@classpath("http://repo1.maven.org/maven2/org/apache/ant/ant/1.8.3/ant-1.8.3.jar")
class """ + className + """(implicit project: Project) {

  Target("phony:hello") help "Say hello" exec {
    AntEcho(message = "Hello!")
  }

}
"""
      val outStream = new PrintStream(new FileOutputStream(projectFile))
      try {
        outStream.print(sbuildStub)
      } finally {
        if (outStream != null) outStream.close()
      }

      System.exit(0)
    }

    val projectReader: ProjectReader = new SimpleProjectReader(config, classpathConfig)

    val project = new Project(projectFile, projectReader)
    config.defines foreach {
      case (key, value) => project.addProperty(key, value)
    }

    projectReader.readProject(project, projectFile)

    verbose("Targets: \n" + project.targets.values.mkString("\n"))

    def formatTargetsOf(p: Project): String = {
      p.targets.values.toSeq.sortBy(_.name).map { t =>
        formatTarget(t)(project) + " \t" + (t.help match {
          case null => ""
          case s: String => s
        })
      }.mkString("\n")
    }

    if (config.listTargets) {
      Console.println(formatTargetsOf(project))
      System.exit(0)
    }
    if (config.listTargetsRecursive) {
      val out = project.projectPool.projects.sortWith {
        case (l, r) if l.eq(project) => true
        case (l, r) if r.eq(project) => false
        case (l, r) => l.projectFile.compareTo(r.projectFile) < 0
      }.map { p => formatTargetsOf(p) }
      Console.println(out.mkString("\n\n"));
      System.exit(0)
    }

    // Targets requested from cmdline
    val (requested: Seq[Target], invalid: Seq[String]) = determineRequestedTargets(config.params)(project)
    if (!invalid.isEmpty) {
      throw new TargetNotFoundException("Invalid target" + (if (invalid.size > 1) "s" else "") + " requested: " + invalid.mkString(", ") + ". For a list of available targets use --list-targets or --list-targets-recursive.");
    }
    val targets = requested.toList

    // Execution plan
    val chain = preorderedDependencies(targets, skipExec = true)(project)

    def execPlan = {
      var line = 0
      "Execution plan: \n" + chain.map { execT =>
        line += 1
        "  " + line + ". " +
          (if (project.projectFile != execT.target.project.projectFile) {
            project.projectDirectory.toURI.relativize(execT.target.project.projectFile.toURI).getPath + "::"
          } else "") +
          TargetRef(execT.target).nameWithoutStandardProto
      }.mkString("\n")
    }
    if (config.showExecutionPlan) {
      Console.println(execPlan)
      System.exit(0)
    } else {
      verbose(execPlan)
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

  def determineRequestedTargets(targets: Seq[String])(implicit project: Project): (Seq[Target], Seq[String]) = {

    // The compile will throw a warning here, but we want this so
    val (requested: Seq[Target], invalid: Seq[String]) =
      targets.map { t =>
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

    (requested, invalid)
    //    if (!invalid.isEmpty) {
    //      throw new TargetNotFoundException("Invalid target" + (if (invalid.size > 1) "s" else "") + " requested: " + invalid.mkString(", ") + ". For a list of available targets use --list-targets or --list-targets-recursive.");
    //    }
    //
    //    requested
  }

  class ExecutedTarget(
    /** The executed target. */
    val target: Target,
    /** <code>true</code> if the target was already up-to-date or determined itself as up-to-date while its execution by setting {@link TargetContext#targetWasUpToDate}. */
    val wasUpToDate: Boolean,
    /** An Id specific for this execution request. */
    val requestId: Option[String])

  class ExecState(var maxCount: Int, var currentNr: Int = 1)

  def formatTarget(target: Target)(implicit project: Project) =
    (
      if (project != target.project) {
        project.projectDirectory.toURI.relativize(target.project.projectFile.toURI).getPath + "::"
      } else ""
    ) + TargetRef(target).nameWithoutStandardProto

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
            if (dependencyTrace.isEmpty) "" else "  <-- as dep of: " + dependencyTrace.map { formatTarget(_) }.mkString(" <- ")
          })
        }

        val root = rootRequest match {
          case Some(root) =>
            if (root == node) {
              val ex = new ProjectConfigurationException("Cycles in dependency chain detected for: " + formatTarget(root))
              ex.buildScript = Some(root.project.projectFile)
              throw ex
            }
            root
          case None => node
        }

        // build prerequisites map

        val alreadyRun: Array[ExecutedTarget] = {

          val skipOrUpToDate = skipExec || Project.isTargetUpToDate(node)
          // Execute prerequisites
          verbose("determine dependencies of: " + node.name)
          val dependencies = node.project.prerequisites(node, searchInAllProjects = true)
          verbose("dependencies of: " + formatTarget(node) + " => " + dependencies.map(formatTarget(_)).mkString(", "))
          
          // detect cycles
          if(dependencies.contains(node)) {
              val ex = new ProjectConfigurationException("Cycles in dependency chain detected. Target " + formatTarget(node) + " contains itself as dependency.")
              ex.buildScript = Some(node.project.projectFile)
              throw ex
          }

          // All direct dependencies share the same request id.
          // Later we can identify them and check, if they were up-to-date.
          val resolveDirectDepsRequestId = Some(UUID.randomUUID.toString)

          val executed = preorderedDependencies(dependencies.toList, Some(root),
            execState = execState,
            skipExec = skipOrUpToDate,
            requestId = resolveDirectDepsRequestId,
            dependencyTrace = node :: dependencyTrace)

          val doContextChecks = true

          verboseTrackDeps("Evaluating up-to-date state of: " + formatTarget(node))

          val execPhonyUpToDateOrSkip = skipOrUpToDate match {
            case true => true // already known up-to-date
            case false => if (!doContextChecks) false else {
              // Evaluate up-to-date state based on the list of executed tasks

              val directDepsExecuted = executed.filter(_.requestId == resolveDirectDepsRequestId)
              val targetWhichWereUpToDateStates: Map[Target, Boolean] =
                directDepsExecuted.toList.groupBy(e => e.target).map {
                  case (t: Target, execs: List[ExecutedTarget]) =>
                    val wasSkipped = execs.forall(_.wasUpToDate)
                    verbose("  Target " + formatTarget(t) + " was skipped: " + wasSkipped)
                    (t -> wasSkipped)
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

              Project.isTargetUpToDate(node, targetWhichWereUpToDateStates)
            }
          }
          if (!skipOrUpToDate && execPhonyUpToDateOrSkip) {
            verbose("All executed phony dependencies of '" + formatTarget(node) + "' were up-to-date.")
          }

          // Print State
          execState map { state =>
            val percent = (state.currentNr, state.maxCount) match {
              case (c, m) if (c > 0 && m > 0) =>
                val p = (c - 1) * 100 / m
                "[" + math.min(100, math.max(0, p)) + "%]"
              case (c, m) => "[" + c + "/" + m + "]"
            }

            if (execPhonyUpToDateOrSkip || node.action == null) {
              verbose(percent + " Skipping target: " + formatTarget(node))
            } else {
              println(percent + " Executing target: " + formatTarget(node))
            }
            state.currentNr += 1
          }

          val ctx = new TargetContextImpl(node)

          if (execPhonyUpToDateOrSkip) {
            ctx.targetWasUpToDate = true
          } else {
            // Need to execute
            node.action match {
              case null => verbose("Nothing to execute for target: " + formatTarget(node))
              case exec =>
                try {
                  verbose("Executing target: " + formatTarget(node))
                  ctx.start
                  exec.apply(ctx)
                  ctx.end
                  verbose("Executed target: " + formatTarget(node) + " in " + ctx.execDurationMSec + " msec")
                  if (ctx.targetWasUpToDate) {
                    verbose("Target determined itself as up-to-date while execution")
                  }
                } catch {
                  case e: Throwable => {
                    ctx.end
                    println("Execution of target " + formatTarget(node) + " aborted after " + ctx.execDurationMSec + " msec with errors: " + e.getMessage);
                    throw e
                  }
                }
            }
          }

          executed ++ Array(
            new ExecutedTarget(
              target = node,
              wasUpToDate = execPhonyUpToDateOrSkip || ctx.targetWasUpToDate,
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