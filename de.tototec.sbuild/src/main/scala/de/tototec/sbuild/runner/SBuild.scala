package de.tototec.sbuild.runner

import scala.annotation.tailrec
import java.io.File
import de.tototec.sbuild._
import de.tototec.cmdoption.CmdOption
import de.tototec.cmdoption.CmdlineParser
import scala.collection.JavaConversions._
import scala.tools.nsc.io.Directory

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

    val chain = preorderedDependencies(targets)

    {
      var line0 = 0
      def line = {
        line0 = line0 + 1
        "  " + line0 + ". "
      }
      verbose("Execution plan: \n" + chain.map(line + _.goal.toString).mkString("\n"))
    }

    var current = 0
    val max = chain.size

    def goalRunner: Target => Unit = { target =>
      current += 1
      lazy val prefix = "[" + current + "/" + max + "] "
      def verbose(msg: => String) = SBuild.verbose(prefix + msg)

      verbose(">>> Processing target: " + target)

      // Should we skip here, after we know that we executed the prerequisites
      //      goal.upToDate match {
      //        case true => verbose("Skip up-to-date goal: " + goal)
      //        case false => 
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
    preorderedDependencies(targets, goalRunner)

    verbose("Finished")
  }

  def determineTargetGoal(targets: Seq[String])(implicit project: Project): Seq[Target] = {

    val (requested: Seq[Target], invalid: Seq[String]) = targets.map { t =>
      project.findTarget(t) match {
        case Some(target) => target
        case None => t
      }
    }.partition(_.isInstanceOf[Target])

    if (!invalid.isEmpty) {
      throw new InvalidCommandlineException("Invalid target" + (if (invalid.size > 1) "s" else "") + " requested: " + invalid.mkString(", "));
    }

    requested
  }

  class ExecutedGoal(val goal: Target, val lastUpdated: Long)

  def preorderedDependencies(request: List[Target], goalRunner: (Target) => Unit = null, rootRequest: Option[Target] = None)(implicit project: Project): List[ExecutedGoal] = {
    request match {
      case Nil => Nil
      case node :: tail => {
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

        val alreadyRun: List[ExecutedGoal] = if (goalRunner != null && node.upToDate) {
          // skip execution of dependencies
          verbose("Skipping execution of goal: " + node + " and all its dependencies")
          List()
        } else {
          // Execute prerequisites
          verbose("checking dependencies of: " + node)
          val dependencies = project.prerequisites(node)
          val executed = preorderedDependencies(dependencies.toList, goalRunner, Some(root))
          if (goalRunner != null) {
            goalRunner.apply(node)
          } else {
            verbose("I would execute goal: " + node)
          }
          executed ++ List(new ExecutedGoal(node, if (node.targetFile.isDefined) { node.targetFile.get.lastModified } else { 0 }))
        }

        alreadyRun ++ preorderedDependencies(tail, goalRunner)
      }
    }
  }

  def verbose(msg: => String) {
    if (verbose) println(msg)
  }

}