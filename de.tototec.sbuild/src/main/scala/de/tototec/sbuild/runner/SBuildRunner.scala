package de.tototec.sbuild.runner

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.PrintStream
import java.lang.reflect.InvocationTargetException
import java.util.Date
import java.util.UUID

import scala.collection.JavaConversions._

import de.tototec.cmdoption.CmdlineParser
import de.tototec.cmdoption.CmdOption
import de.tototec.sbuild._
import org.fusesource.jansi.AnsiConsole
import org.fusesource.jansi.Ansi

object SBuildRunner extends SBuildRunner {

  def main(args: Array[String]) {
    AnsiConsole.systemInstall
    try {
      val retval = run(args)
      sys.exit(retval)
    } finally {
      AnsiConsole.systemUninstall
    }
  }

}

class SBuildRunner {

  private[runner] var verbose = false

  private var log: SBuildLogger = new SBuildConsoleLogger(LogLevel.Info)

  def createSBuildStub(projectFile: File, stubDir: File) {
    if (projectFile.exists) {
      throw new SBuildException("File '" + projectFile.getName + "' already exists. ")
    }

    val sbuildStub = new File(stubDir, projectFile.getName) match {

      case stubFile if stubFile.exists =>
        val source = io.Source.fromFile(stubFile)
        val text = source.mkString
        source.close
        text

      case _ =>
        val className = if (projectFile.getName.endsWith(".scala")) {
          projectFile.getName.substring(0, projectFile.getName.length - 6)
        } else { projectFile.getName }

        s"""|import de.tototec.sbuild._
          |import de.tototec.sbuild.TargetRefs._
          |import de.tototec.sbuild.ant._
          |import de.tototec.sbuild.ant.tasks._
          |
          |@version("${SBuildVersion.osgiVersion}")
          |@classpath("mvn:org.apache.ant:ant:1.8.4")
          |class ${className}(implicit _project: Project) {
          |
          |  Target("phony:hello") help "Say hello" exec {
          |    AntEcho(message = "Hello!")
          |  }
          |
          |}
          |""".stripMargin
    }

    val outStream = new PrintStream(new FileOutputStream(projectFile))
    try {
      outStream.print(sbuildStub)
    } finally {
      if (outStream != null) outStream.close
    }
  }

  def run(args: Array[String]): Int = {
    val bootstrapStart = System.currentTimeMillis

    val aboutAndVersion = "SBuild " + SBuildVersion.version + " (c) 2011 - 2013, ToToTec GbR, Tobias Roeser"

    val config = new Config()
    val classpathConfig = new ClasspathConfig()
    val cmdlineConfig = new {
      @CmdOption(names = Array("--version"), isHelp = true, description = "Show SBuild version.")
      var showVersion = false

      @CmdOption(names = Array("--help", "-h"), isHelp = true, description = "Show this help screen.")
      var help = false

      @CmdOption(names = Array("--no-color"), description = "Disable colored output.")
      var noColor = false
    }
    val cp = new CmdlineParser(config, classpathConfig, cmdlineConfig)
    cp.setResourceBundle("de.tototec.sbuild.runner.Messages", getClass.getClassLoader())
    cp.setAboutLine(aboutAndVersion)
    cp.setProgramName("sbuild")

    cp.parse(args: _*)

    if (cmdlineConfig.noColor)
      Ansi.setEnabled(false)

    if (cmdlineConfig.help) {
      cp.usage
      return 0
    }

    if (cmdlineConfig.showVersion) {
      println(aboutAndVersion)
      return 0
    }

    def errorOutput(e: Throwable, msg: String = null) = {
      Console.err.println

      if (msg != null)
        Console.err.println(fError(msg))

      if (e.isInstanceOf[BuildScriptAware] && e.asInstanceOf[BuildScriptAware].buildScript.isDefined)
        Console.err.println(fError("Project: ") + fErrorEmph(e.asInstanceOf[BuildScriptAware].buildScript.get.getPath).toString)

      if (e.isInstanceOf[TargetAware] && e.asInstanceOf[TargetAware].targetName.isDefined)
        Console.err.println(fError("Target:  ") + fErrorEmph(e.asInstanceOf[TargetAware].targetName.get).toString)

      Console.err.println(fError("Details: " + e.getLocalizedMessage))
    }

    try {
      run(config = config, classpathConfig = classpathConfig, bootstrapStart = bootstrapStart)
    } catch {
      case e: ProjectConfigurationException =>
        errorOutput(e, "SBuild detected a failure in the project configuration or the build scripts.")
        if (verbose) throw e
        1
      case e: TargetNotFoundException =>
        errorOutput(e, "SBuild failed because an invalid target was requested. For a list of available targets use --list-targets or --list-targets-recursive. Use --help for a list of other commandline options.")
        if (verbose) throw e
        1
      case e: ExecutionFailedException =>
        errorOutput(e, "SBuild detected a failure while execution a target.")
        if (verbose) throw e
        1
      case e: Exception =>
        errorOutput(e, "SBuild failed with an unexpected exception (" + e.getClass.getSimpleName + ").")
        if (verbose) throw e
        1
    }
  }

  def run(config: Config, classpathConfig: ClasspathConfig, bootstrapStart: Long = System.currentTimeMillis): Int = {

    SBuildRunner.verbose = config.verbose
    if (verbose) {
      log = new SBuildConsoleLogger(LogLevel.Info, LogLevel.Debug)
      Util.log = log
    }

    val projectFile = new File(config.buildfile)

    if (config.createStub) {
      createSBuildStub(projectFile, new File(classpathConfig.sbuildHomeDir, "stub"))
      return 0
    }

    val projectReader: ProjectReader = new SimpleProjectReader(config, classpathConfig, log)

    val project = new Project(projectFile, projectReader, None, log)
    config.defines foreach {
      case (key, value) => project.addProperty(key, value)
    }

    projectReader.readProject(project, projectFile)

    val additionalProjects = config.additionalBuildfiles.map { buildfile =>
      project.findModule(buildfile) match {
        case None =>
          // Create and add new module and copy configs
          val module = project.findOrCreateModule(new File(buildfile).getAbsolutePath, copyProperties = false)
          config.defines foreach {
            case (key, value) => module.addProperty(key, value)
          }
          module

        case Some(module) => module // Module already defined
      }
    }

    log.log(LogLevel.Debug, "Targets: \n" + project.targets.values.mkString("\n"))

    /**
     * Format a target relative to the base project <code>p</code>.
     */
    def formatTargetsOf(p: Project): String = {
      p.targets.values.toSeq.filter(!_.isImplicit).sortBy(_.name).map { t =>
        formatTarget(t)(project) + " \t" + (t.help match {
          case null => ""
          case s: String => s
        })
      }.mkString("\n")
    }

    def projectSorter(l: Project, r: Project): Boolean = (l, r) match {
      // ensure main project file is on top
      case (l, r) if l.eq(project) => true
      case (l, r) if r.eq(project) => false
      case (l, r) => l.projectFile.compareTo(r.projectFile) < 0
    }

    // Format listing of target and return
    if (config.listTargets || config.listTargetsRecursive) {
      val projectsToList = if (config.listTargetsRecursive) {
        project.projectPool.projects
      } else {
        Seq(project) ++ additionalProjects
      }
      val out = projectsToList.sortWith(projectSorter _).map { p => formatTargetsOf(p) }
      Console.println(out.mkString("\n\n"))
      // early exit
      return 0
    }

    if (config.listModules) {
      val moduleNames = project.projectPool.projects.sortWith(projectSorter _).map {
        p => formatProject(p)(project)
      }
      Console.println(moduleNames.mkString("\n"))
      return 0
    }

    // Check targets requested from cmdline an throw a exception, if invalid targets were requested
    val (requested: Seq[Target], invalid: Seq[String]) = determineRequestedTargets(config.params)(project)
    if (!invalid.isEmpty) {
      throw new TargetNotFoundException("Invalid target" + (if (invalid.size > 1) "s" else "") + " requested: " + invalid.mkString(", ") + ".");
    }
    val targets = requested.toList

    // The dependencyTree will be populated by the treePrinter, in case it was requested on commandline
    var dependencyTree = Seq[(Int, Target)]()
    val treePrinter = config.showDependencyTree match {
      case true => Some((depth: Int, node: Target) => { dependencyTree ++= Seq((depth, node)) })
      case _ => None
    }

    // The execution plan (chain) will be evaluated on first need
    lazy val chain: Array[ExecutedTarget] = {
      if (!targets.isEmpty && !config.noProgress) {
        log.log(LogLevel.Info, "Calculating dependency tree...")
      }
      preorderedDependenciesForest(targets, skipExec = true, treePrinter = treePrinter)(project)
    }

    // Execution plan
    def execPlan = {
      var line = 0
      "Execution plan: \n" + chain.map { execT =>
        line += 1
        "  " + line + ". " + formatTarget(execT.target)(project)
      }.mkString("\n")
    }
    if (config.showExecutionPlan) {
      Console.println(execPlan)
      // early exit
      return 0
    } else {
      log.log(LogLevel.Debug, execPlan)
    }

    if (config.showDependencyTree) {
      // trigger lazy evaluated chain
      chain
      val showGoUp = true
      var lastDepth = 0
      var lastShown = Map[Int, Target]()
      val output = "Dependency tree: \n" + dependencyTree.map {
        case (depth, target) =>

          var prefix = if (lastDepth > depth && depth > 0) {
            List.fill(depth - 1)("  ").mkString + "  (" + formatTarget(lastShown(depth - 1))(project) + ")\n"
          } else ""

          lastDepth = depth
          lastShown += (depth -> target)
          prefix + List.fill(depth)("  ").mkString + "  " + formatTarget(target)(project)
      }.mkString("\n")
      Console.println(output)
      // early exit
      return 0
    }

    if (config.check || config.checkRecusive) {

      val projectsToCheck = if (config.checkRecusive) {
        project.projectPool.projects
      } else {
        Seq(project) ++ additionalProjects
      }

      val targetsToCheck = projectsToCheck.sortWith(projectSorter _).flatMap { p =>
        p.targets.values.filterNot(_.isImplicit)
      }
      // Console.println("About to check targets: "+targetsToCheck.mkString(", "))
      var errors: Seq[(Target, String)] = Seq()
      targetsToCheck.foreach { target =>
        Console.print("Checking target: " + formatTarget(target)(project))
        try {
          preorderedDependenciesTree(curTarget = target, skipExec = true)(project)
          Console.println("  \t" + fOk("OK"))
        } catch {
          case e: SBuildException =>
            Console.println("  \t" + fError("FAILED: " + e.getMessage))
            errors ++= Seq(target -> e.getMessage)
        }
      }
      if (!errors.isEmpty) Console.println(s"Found the following ${fError(errors.size.toString)} problems:")
      errors.foreach {
        case (target, message) =>
          Console.println(fError(formatTarget(target)(project) + ": " + message))
      }
      // When errors, then return with 1 else with 0
      return if (errors.isEmpty) 0 else 1
    }

    // force evaluation of lazy val chain, if required, and switch afterwards from bootstrap to execution time benchmarking.
    val execProgress = if (config.noProgress) None else Some(new ExecProgress(maxCount = chain.size))

    val executionStart = System.currentTimeMillis
    val bootstrapTime = executionStart - bootstrapStart

    if (!targets.isEmpty && !config.noProgress) {
      log.log(LogLevel.Info, "Executing...")
    }

    preorderedDependenciesForest(targets, execProgress = execProgress)(project)
    if (!targets.isEmpty && !config.noProgress) {
      println(fPercent("[100%]") + " Execution finished. SBuild init time: " + bootstrapTime +
        " msec, Execution time: " + (System.currentTimeMillis - executionStart) + " msec")
    }

    log.log(LogLevel.Debug, "Finished")
    // return with 0, indicating no errors
    0
  }

  def determineRequestedTargets(targets: Seq[String])(implicit project: Project): (Seq[Target], Seq[String]) = {

    // The compile will throw a warning here, so we use the erasure version and keep the intent as comment
    // val (requested: Seq[Target], invalid: Seq[String]) =
    val (requested, invalid) = targets.map { t =>
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

    (requested.asInstanceOf[Seq[Target]], invalid.asInstanceOf[Seq[String]])
  }

  class ExecutedTarget(
    /** The executed target. */
    val target: Target,
    /** An Id specific for this execution request. */
    val requestId: Option[String],
    val ran: Boolean,
    val targetContext: TargetContext)

  class ExecProgress(var maxCount: Int, var currentNr: Int = 1)

  def formatProject(project: Project)(implicit baseProject: Project) =
    if (baseProject != project)
      baseProject.projectDirectory.toURI.relativize(project.projectFile.toURI).getPath
    else project.projectFile.getName

  def formatTarget(target: Target)(implicit project: Project) =
    (if (project != target.project) {
      project.projectDirectory.toURI.relativize(target.project.projectFile.toURI).getPath + "::"
    } else "") + TargetRef(target).nameWithoutStandardProto
  /**
   * Visit a forest of targets, each target of parameter <code>request</code> is the root of a tree.
   * Each tree will search deep-first. If parameter <code>skipExec</code> is <code>true</code>, the associated actions will not executed.
   * If <code>skipExec</code> is <code>false</code>, for each target the up-to-date state will be evaluated,
   * and if the target is no up-to-date, the associated action will be executed.
   */
  def preorderedDependenciesForest(request: List[Target],
                                   execProgress: Option[ExecProgress] = None,
                                   skipExec: Boolean = false,
                                   requestId: Option[String] = None,
                                   dependencyTrace: List[Target] = List(),
                                   depth: Int = 0,
                                   treePrinter: Option[(Int, Target) => Unit] = None)(implicit project: Project): Array[ExecutedTarget] = {
    request match {
      case Nil => Array()
      case firstTarget :: otherTargets =>

        // walk the current target (deep first)
        val alreadyRun = preorderedDependenciesTree(
          curTarget = firstTarget,
          execProgress = execProgress,
          skipExec = skipExec,
          requestId = requestId,
          dependencyTrace = dependencyTrace,
          depth = depth,
          treePrinter = treePrinter
        )

        // and then walk other requested
        alreadyRun ++ preorderedDependenciesForest(otherTargets,
          execProgress = execProgress,
          skipExec = skipExec,
          dependencyTrace = dependencyTrace,
          requestId = requestId,
          depth = depth,
          treePrinter = treePrinter)
    }
  }

  /**
   * Visit each target of tree <code>node</code> deep-first.
   * If parameter <code>skipExec</code> is <code>true</code>, the associated actions will not executed.
   * If <code>skipExec</code> is <code>false</code>, for each target the up-to-date state will be evaluated,
   * and if the target is no up-to-date, the associated action will be executed.
   */
  def preorderedDependenciesTree(curTarget: Target,
                                 execProgress: Option[ExecProgress] = None,
                                 skipExec: Boolean = false,
                                 requestId: Option[String] = None,
                                 dependencyTrace: List[Target] = List(),
                                 depth: Int = 0,
                                 treePrinter: Option[(Int, Target) => Unit] = None)(implicit project: Project): Array[ExecutedTarget] = {

    treePrinter match {
      case Some(printFunc) => printFunc(depth, curTarget)
      case _ =>
    }

    // check for cycles
    dependencyTrace.find(dep => dep == curTarget) match {
      case Some(cycle) =>
        val ex = new ProjectConfigurationException("Cycles in dependency chain detected for: " + formatTarget(cycle) +
          ". The dependency chain: " + (curTarget :: dependencyTrace).reverse.map(d => formatTarget(d)).mkString(" -> "))
        ex.buildScript = Some(cycle.project.projectFile)
        throw ex
      case _ =>
    }

    // build prerequisites map

    //  val skipOrUpToDate = skipExec || Project.isTargetUpToDate(node, searchInAllProjects = true)
    // Execute prerequisites
    // log.log(LogLevel.Debug, "determine dependencies of: " + node.name)
    val dependencies: List[Target] = try {
      curTarget.project.prerequisites(target = curTarget, searchInAllProjects = true)
    } catch {
      case e: UnsupportedSchemeException =>
        val ex = new UnsupportedSchemeException("Unsupported Scheme in dependencies of target: " +
          formatTarget(curTarget) + ". " + e.getMessage)
        ex.buildScript = e.buildScript
        throw ex
    }
    log.log(LogLevel.Debug, "dependencies of: " + formatTarget(curTarget) + " => " + dependencies.map(formatTarget(_)).mkString(", "))

    // All direct dependencies share the same request id.
    // Later we can identify them and check, if they were up-to-date.
    val resolveDirectDepsRequestId = Some(UUID.randomUUID.toString)

    val executedDependencies: Array[ExecutedTarget] = preorderedDependenciesForest(
      request = dependencies,
      execProgress = execProgress,
      skipExec = skipExec,
      requestId = resolveDirectDepsRequestId,
      dependencyTrace = curTarget :: dependencyTrace,
      depth = depth + 1,
      treePrinter = treePrinter)

    log.log(LogLevel.Debug, "Executed dependency count: " + executedDependencies.size);

    //          verboseTrackDeps("Evaluating up-to-date state of: " + formatTarget(node))

    // print dep-tree
    lazy val trace = dependencyTrace match {
      case Nil => ""
      case x =>
        var _prefix = "     "
        def prefix = {
          _prefix += "  "
          _prefix
        }
        x.map { "\n" + prefix + formatTarget(_) }.mkString
    }

    val ctx = new TargetContextImpl(curTarget)

    if (!skipExec) this.log.log(LogLevel.Debug, "===> " + formatTarget(curTarget) +
      " is current execution, with tree: " + trace + " <===")

    val executeCurTarget = if (skipExec) {
      // already known as up-to-date
      false
    } else {
      // not skipped execution, determine if dependencies were up-to-date

      log.log(LogLevel.Debug, "Request-ID used for dependencies: " + resolveDirectDepsRequestId)
      val directDepsExecuted = executedDependencies.filter(_.requestId == resolveDirectDepsRequestId)

      lazy val depsLastModified: Long = dependenciesLastModified(directDepsExecuted)
      log.log(LogLevel.Debug, s"Evaluated last modified value '${depsLastModified}' for dependencies: " + directDepsExecuted.map { d => formatTarget(d.target) }.mkString(","))

      val needsToRun: Boolean = curTarget.targetFile match {
        case Some(file) =>
          // file target
          !file.exists || file.lastModified < depsLastModified

        case None if curTarget.action == null =>
          // phony target but just a collector of dependencies
          ctx.targetLastModified = depsLastModified
          false

        case None =>
          // phony target, have to run it always. Any laziness is up to it implementation
          true
      }

      needsToRun
      //      
      //      // not up-to-date but we check the context
      //      // Evaluate up-to-date state based on the list of already executed tasks
      //      // only use direct dependencies
      //      //                log.log(LogLevel.Debug, "Existing request ID -> count: " + executed.groupBy { et: ExecutedTarget => et.requestId }.map { case (k, vl) => (k, vl.size) })
      //      // group direct dependencies by target and then return a map with the up-to-date state for each target
      //      val targetWhichWereUpToDateStates: Map[Target, Boolean] =
      //        directDepsExecuted.toList.groupBy(e => e.target).map {
      //          case (t: Target, execs: List[ExecutedTarget]) =>
      //            val wasSkipped = execs.forall(_.wasUpToDate)
      //            log.log(LogLevel.Debug, "  Direct dependency " + formatTarget(t) + (if (wasSkipped) " was skipped " else " was not skipped"))
      //            (t -> wasSkipped)
      //        }
      //
      //      Target.isUpToDate(curTarget, targetWhichWereUpToDateStates, searchInAllProjects = true)
    }
    if (!skipExec && !executeCurTarget) {
      log.log(LogLevel.Debug, "Target '" + formatTarget(curTarget) + "' does not need to run.")
    }

    // Print State
    execProgress map { state =>
      val progress = (state.currentNr, state.maxCount) match {
        case (c, m) if (c > 0 && m > 0) =>
          val p = (c - 1) * 100 / m
          fPercent("[" + math.min(100, math.max(0, p)) + "%]")
        case (c, m) => "[" + c + "/" + m + "]"
      }

      if (executeCurTarget) {
        val ft = if (dependencyTrace.isEmpty) { fMainTarget _ } else { fTarget _ }
        println(progress + " Executing target: " + ft(formatTarget(curTarget)))
      } else {
        log.log(LogLevel.Debug, progress + " Skipping target: " + formatTarget(curTarget))
      }

      state.currentNr += 1
    }

    var ran: Boolean = false

    if (executeCurTarget) {
      curTarget.action match {
        case null =>
          log.log(LogLevel.Debug, "Nothing to execute (no action defined) for target: " + formatTarget(curTarget))
        case exec =>
          try {
            log.log(LogLevel.Debug, "Executing target: " + formatTarget(curTarget))
            ctx.start
            ran = true
            exec.apply(ctx)
            ctx.end
            log.log(LogLevel.Debug, s"Executed target '${formatTarget(curTarget)}' in ${ctx.execDurationMSec} msec")
            ctx.targetLastModified match {
              case Some(lm) =>
                log.log(LogLevel.Debug, s"The context of target '${formatTarget(curTarget)}' reports a last modified value of '${lm}'. Request-ID: ${requestId}")
              case _ =>
            }
          } catch {
            case e: TargetAware =>
              ctx.end
              e.targetName = Some(formatTarget(curTarget))
              println(s"Execution of target '${formatTarget(curTarget)}' aborted after ${ctx.execDurationMSec} msec with errors: ${e.getMessage}")
              throw e
            case e: Throwable =>
              ctx.end
              println(s"Execution of target '${formatTarget(curTarget)}' aborted after ${ctx.execDurationMSec} msec with errors: ${e.getMessage}")
              throw e
          }
      }
    }

    executedDependencies ++ Array(
      new ExecutedTarget(
        target = curTarget,
        requestId = requestId,
        ran = ran,
        targetContext = ctx
      )
    )

  }

  def dependenciesLastModified(dependencies: Array[ExecutedTarget])(implicit project: Project): Long = {
    var lastModified: Long = 0
    def updateLastModified(lm: Long) {
      lastModified = math.max(lastModified, lm)
    }
    dependencies.foreach { dep =>
      dep.target.targetFile match {
        case Some(file) if !file.exists =>
          log.log(LogLevel.Info, s"""The file "${file}" created by dependency "${formatTarget(dep.target)}" does no longer exists.""")
          updateLastModified(Long.MaxValue)
        case Some(file) =>
          // file target and file exists, so we use it last modified
          updateLastModified(file.lastModified)
        case None =>
          // phony target, so we ask its target context 
          dep.targetContext.targetLastModified match {
            case Some(lm) =>
              // context has an associated last modified, which we will use
              updateLastModified(lm)
            case None =>
              // target context does not know something, so we fall back to a last modified of NOW
              updateLastModified(Long.MaxValue)
          }
      }
    }
    lastModified
  }

  import org.fusesource.jansi.Ansi._
  import org.fusesource.jansi.Ansi.Color._

  def fPercent(text: String) = ansi.fgBright(CYAN).a(text).fg(DEFAULT)
  def fTarget(text: String) = ansi.fg(GREEN).a(text).fg(DEFAULT)
  def fMainTarget(text: String) = ansi.fg(GREEN).bold.a(text).boldOff.fg(DEFAULT)
  def fOk(text: String) = ansi.fgBright(GREEN).a(text).fg(DEFAULT)
  def fError(text: String) = ansi.fgBright(RED).a(text).fg(DEFAULT)
  def fErrorEmph(text: String) = ansi.fgBright(RED).bold.a(text).boldOff.fg(DEFAULT)

}
