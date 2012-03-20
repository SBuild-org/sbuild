package de.tototec.sbuild.runner

import scala.annotation.tailrec
import java.io.File
import de.tototec.sbuild._
import de.tototec.cmdoption.CmdOption
import de.tototec.cmdoption.CmdlineParser
import scala.collection.JavaConversions._

object SBuild {

  val version = "0.0.1"

  private[runner] var verbose = false

  class Config {
    @CmdOption(names = Array("--help"), isHelp = true)
    var help = false

    @CmdOption(names = Array("--build-file", "--buildfile", "-f"), args = Array("FILE"))
    var buildfile = "SBuild.scala"

    @CmdOption(names = Array("--verbose", "-v"))
    var verbose = false

    @CmdOption(names = Array("--compile-cp"), args = Array("CLASSPATH"))
    var compileClasspath = "target/classes"

    @CmdOption(args = Array("PARAM"))
    val params = new java.util.LinkedList[String]()
  }

  def main(args: Array[String]) {

    val config = new Config()
    val cp = new CmdlineParser(config)
    cp.parse(args: _*)
    
    SBuild.verbose = config.verbose
    
    if(config.help) {
      cp.usage
      System.exit(0)
    }
    
    implicit val project = new Project()
    val script = new ProjectScript(new File(config.buildfile), config.compileClasspath)
    //    script.interpret
    script.compileAndExecute(project)

    verbose("Targets: \n" + project.targets.mkString("\n"))

    val targets = determineTargetGoal(config.params).toList

    val chain = preorderedDependencies(targets)

    var line0 = 0
    def line = {
      line0 = line0 + 1
      "  " + line0 + ". "
    }
    verbose("Execution plan: \n" + chain.map(line + _.goal.toString).mkString("\n"))

    var current = 0
    val max = chain.size

    def goalRunner: Target => Unit = { goal =>
      current += 1
      lazy val prefix = "[" + current + "/" + max + "] "
      def verbose(msg: => String) = SBuild.verbose(prefix + msg)

      verbose(">>> Processing goal: " + goal)

      // Should we skip here, after we know that we executed the prerequisites
      //      goal.upToDate match {
      //        case true => verbose("Skip up-to-date goal: " + goal)
      //        case false => 
      goal.action match {
        case null => verbose("Nothing to execute for goal: " + goal)
        case exec: (() => Unit) => {
          verbose("Executing goal: " + goal)
          try {
            exec.apply
            verbose("Executed goal: " + goal)
          } catch {
            case e: Throwable => {
              verbose("Execution aborted with errors: " + e.getMessage);
              throw e
            }
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

    val (requested: Seq[Target], invalid: Seq[String]) = targets.map(g => project.findTarget(g) match {
      case Some(goal) => goal
      case None => g
    }).partition(_.isInstanceOf[Target])

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
          case Some(root) => {
            if (root.filePath == node.filePath) {
              throw new RuntimeException("Cycles in dependency chain detected for: " + root)
            }
            root
          }
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