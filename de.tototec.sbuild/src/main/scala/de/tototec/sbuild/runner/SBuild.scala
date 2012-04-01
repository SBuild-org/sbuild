package de.tototec.sbuild.runner

import java.io.File

import scala.Array.canBuildFrom
import scala.collection.JavaConversions.asScalaBuffer
import scala.collection.JavaConversions.mapAsScalaMap
import scala.tools.nsc.io.Path.string2path
import scala.tools.nsc.io.Directory

import de.tototec.cmdoption.CmdOption
import de.tototec.cmdoption.CmdlineParser
import de.tototec.sbuild.TargetRef.fromString
import de.tototec.sbuild.InvalidCommandlineException
import de.tototec.sbuild.Project
import de.tototec.sbuild.Target
import de.tototec.sbuild.TargetRef

object SBuild {

  val version = "0.0.1"

  private[runner] var verbose = false

  class Config {
    @CmdOption(names = Array("--help", "-h"), isHelp = true, description = "Show this help screen.")
    var help = false

    @CmdOption(names = Array("--buildfile", "-f"), args = Array("FILE"),
      description = "The buildfile to use (default: SBuild.scala).")
    var buildfile = "SBuild.scala"

    @CmdOption(names = Array("--verbose", "-v"), description = "Be verbose when running.")
    var verbose = false

    // The classpath is used when SBuild compiles the buildfile
    @CmdOption(names = Array("--compile-cp"), args = Array("CLASSPATH"), hidden = true)
    var compileClasspath = "target/classes"

    @CmdOption(names = Array("--list-targets", "-l"),
      description = "Show a list of targets defined in the current buildfile")
    val listTargets = false

    @CmdOption(names = Array("--define", "-D"), args = Array("KEY=VALUE"), maxCount = -1,
      description = "Define or override properties. If VALUE is omitted it defaults to \"true\".")
    def addDefine(keyValue: String) {
      keyValue.split("=", 2) match {
        case Array(key, value) => defines.put(key, value)
        case Array(key) => defines.put(key, "true")
      }
    }
    val defines: java.util.Map[String, String] = new java.util.LinkedHashMap()

    @CmdOption(names = Array("--clean"),
      description = "Remove all generated output and caches before start. This will force a new compile of the buildfile.")
    var clean: Boolean = false

    @CmdOption(names = Array("--use-classloader-hack"), args = Array("true|false"), maxCount = -1,
      description = "The classloader hack is currently needed to work around an unsolve Classloader problem (default: true).")
    var useClassloaderHack: Boolean = true

    @CmdOption(args = Array("TARGETS"), maxCount = -1, description = "The target(s) to execute (in order).")
    val params = new java.util.LinkedList[String]()
  }

  def main(args: Array[String]) {
    val config = new Config()
    val cp = new CmdlineParser(config)
    cp.parse(args: _*)

    SBuild.verbose = config.verbose

    if (config.help) {
      cp.usage
      System.exit(0)
    }

    if (config.useClassloaderHack) {
      val scriptCL = new SBuildURLClassLoader(config.compileClasspath.split(":").map { new File(_).toURI.toURL }, null)
      scriptCL.loadClass("de.tototec.sbuild.runner.SBuild").getMethod("main0", classOf[Array[String]]).invoke(null, args)
    } else {
      main0(args)
    }
  }

  def main0(args: Array[String]) {

    val config = new Config()
    val cp = new CmdlineParser(config)
    cp.parse(args: _*)

    SBuild.verbose = config.verbose

    if (config.help) {
      cp.usage
      System.exit(0)
    }

    implicit val project = new Project(Directory(System.getProperty("user.dir")))
    config.defines foreach {
      case (key, value) => project.addProperty(key, value)
    }

    val script = new ProjectScript(new File(config.buildfile), config.compileClasspath)
    if (config.clean) {
      script.clean
    }
    //    script.interpret
    val scriptInstance = script.compileAndExecute(project, config.useClassloaderHack)

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

    val targets = determineTargetGoal(config.params).toList

    val chain = preorderedDependencies(targets, skipExec = true)

    {
      var line0 = 0
      def line = {
        line0 = line0 + 1
        "  " + line0 + ". "
      }
      verbose("Execution plan: \n" + chain.map(line + _.goal.toString).mkString("\n"))
    }

    def goalRunner: Target => Unit = { target =>
      target.action match {
        case null => verbose("Nothing to execute for target: " + target)
        case exec: (() => Unit) => {
          val time = System.currentTimeMillis
          //          val origCl = Thread.currentThread.getContextClassLoader
          try {
            //            Thread.currentThread.setContextClassLoader(scriptInstance.getClass.getClassLoader)
            verbose("Executing target: " + target)
            exec.apply
            verbose("Executed target: " + target + " in " + (System.currentTimeMillis - time) + " msec")
          } catch {
            case e: Throwable => {
              verbose("Execution of target " + target + " aborted with errors: " + e.getMessage);
              throw e
            }
            //          } finally {
            //            Thread.currentThread.setContextClassLoader(origCl)
          }
        }
        //        }
      }
    }

    verbose("Executing...")
    preorderedDependencies(targets, goalRunner, execState = Some(new ExecState(maxCount = chain.size)))

    verbose("Finished")
  }

  def determineTargetGoal(targets: Seq[String])(implicit project: Project): Seq[Target] = {

    val (requested: Seq[Target], invalid: Seq[String]) = targets.map { t =>
      project.findTarget(t) match {
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

  class ExecutedGoal(val goal: Target, val lastUpdated: Long, val needsExec: Boolean)

  class ExecState(var maxCount: Int, var currentNr: Int = 1)

  def preorderedDependencies(request: List[Target], goalRunner: (Target) => Unit = null, rootRequest: Option[Target] = None, execState: Option[ExecState] = None, skipExec: Boolean = false)(implicit project: Project): Array[ExecutedGoal] = {
    request match {
      case Nil => Array()
      case node :: tail =>

        // detect collisions

        val root = rootRequest match {
          case Some(root) =>
            if (root == node) {
              throw new RuntimeException("Cycles in dependency chain detected for: " + root)
            }
            root
          case None => node
        }

        // build prerequisites map

        val alreadyRun: Array[ExecutedGoal] =
          //          if (goalRunner != null && node.upToDate) {
          //            // skip execution of dependencies
          //            verbose("Skipping execution of goal: " + node + " and all its dependencies")
          //            Array()
          //          } else
          {
            val skipOrUpToDate = skipExec || node.upToDate
            // Execute prerequisites
            verbose("checking dependencies of: " + node)
            val dependencies = project.prerequisites(node)

            val executed = preorderedDependencies(dependencies.toList, goalRunner, Some(root),
              execState = execState,
              skipExec = skipOrUpToDate)

            // Print State
            execState map { state =>
              val percent = (state.currentNr, state.maxCount) match {
                case (c, m) if (c > 0 && m > 0) =>
                  val p = (c - 1) * 100 / m
                  "[" + math.min(100, math.max(0, p)) + "%]"
                case (c, m) => "[" + c + "/" + m + "]"
              }
              if (!skipOrUpToDate) {
                println(percent + " Executing target '" + TargetRef(node).nameWithoutProto + "':")
              } else {
                verbose(percent + " Skipping target '" + TargetRef(node).nameWithoutProto + "'")
              }
              state.currentNr += 1
            }

            if (!skipOrUpToDate) {
              if (goalRunner != null) {
                goalRunner.apply(node)
                // } else {
                // verbose("I would execute goal: " + node)
              }
            }
            executed ++ Array(new ExecutedGoal(node, if (node.targetFile.isDefined) { node.targetFile.get.lastModified } else { 0 }, needsExec = !skipOrUpToDate))
          }

        alreadyRun ++ preorderedDependencies(tail, goalRunner, execState = execState, skipExec = skipExec)
    }
  }

  def verbose(msg: => String) {
    if (verbose) println(msg)
  }

}