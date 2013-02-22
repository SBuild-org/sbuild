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
import java.io.FileWriter
import scala.io.Source
import scala.util.Try

object SBuildRunner extends SBuildRunner {

  def main(args: Array[String]) {
    AnsiConsole.systemInstall
    val retval = run(args)
    AnsiConsole.systemUninstall
    sys.exit(retval)
  }

}

class SBuildRunner {

  private[runner] var verbose = false

  private var log: SBuildLogger = new SBuildConsoleLogger(LogLevel.info)

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
      log.log(LogLevel.Error, "")

      if (msg != null)
        log.log(LogLevel.Error, fError(msg).toString)

      if (e.isInstanceOf[BuildScriptAware] && e.asInstanceOf[BuildScriptAware].buildScript.isDefined)
        log.log(LogLevel.Error, fError("Project: ").toString + fErrorEmph(e.asInstanceOf[BuildScriptAware].buildScript.get.getPath).toString)

      if (e.isInstanceOf[TargetAware] && e.asInstanceOf[TargetAware].targetName.isDefined)
        log.log(LogLevel.Error, fError("Target:  ").toString + fErrorEmph(e.asInstanceOf[TargetAware].targetName.get).toString)

      log.log(LogLevel.Error, fError("Details: " + e.getLocalizedMessage).toString)
    }

    try {
      run(config = config, classpathConfig = classpathConfig, bootstrapStart = bootstrapStart)
    } catch {
      case e: InvalidApiUsageException =>
        errorOutput(e, "SBuild detected a invalid usage of SBuild API. Please consult the API Refence Documentation at http://sbuild.tototec.de .")
        if (verbose) throw e
        1
      case e: ProjectConfigurationException =>
        errorOutput(e, "SBuild detected a failure in the project configuration or the build scripts.")
        if (verbose) throw e
        1
      case e: TargetNotFoundException =>
        errorOutput(e, "SBuild failed because an invalid target was requested. For a list of available targets use --list-targets or --list-targets-recursive. Use --help for a list of other commandline options.")
        if (verbose) throw e
        1
      case e: ExecutionFailedException =>
        errorOutput(e, "SBuild detected a failure in a target execution.")
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
      log = new SBuildConsoleLogger(LogLevel.debug)
      Util.log = log
    }

    val projectFile = new File(config.buildfile)

    if (config.createStub) {
      createSBuildStub(projectFile, new File(classpathConfig.sbuildHomeDir, "stub"))
      return 0
    }

    val projectReader: ProjectReader = new SimpleProjectReader(classpathConfig, log, clean = config.clean)

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
      log.log(LogLevel.Info, out.mkString("\n\n"))
      // early exit
      return 0
    }

    if (config.listModules) {
      val moduleNames = project.projectPool.projects.sortWith(projectSorter _).map {
        p => formatProject(p)(project)
      }
      log.log(LogLevel.Info, moduleNames.mkString("\n"))
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
      log.log(LogLevel.Info, execPlan)
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
      log.log(LogLevel.Info, output)
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
        log.log(LogLevel.Info, "Checking target: " + formatTarget(target)(project))
        try {
          preorderedDependenciesTree(curTarget = target, skipExec = true)(project)
          log.log(LogLevel.Info, "  \t" + fOk("OK"))
        } catch {
          case e: SBuildException =>
            log.log(LogLevel.Info, "  \t" + fError("FAILED: " + e.getMessage))
            errors ++= Seq(target -> e.getMessage)
        }
      }
      if (!errors.isEmpty) log.log(LogLevel.Error, s"Found the following ${fError(errors.size.toString)} problems:")
      errors.foreach {
        case (target, message) =>
          log.log(LogLevel.Error, fError(formatTarget(target)(project) + ": " + message).toString)
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

  def determineRequestedTarget(target: String, searchInAllProjects: Boolean = false)(implicit project: Project): Either[String, Target] =
    project.findTarget(target, searchInAllProjects = searchInAllProjects) match {
      case Some(target) => Right(target)
      case None => TargetRef(target).explicitProto match {
        case None | Some("phony") | Some("file") => Left(target)
        case _ =>
          // A scheme handler might be able to resolve this thing
          Right(project.createTarget(TargetRef(target)))
      }
    }

  def determineRequestedTargets(targets: Seq[String])(implicit project: Project): (Seq[Target], Seq[String]) = {

    // The compile will throw a warning here, so we use the erasure version and keep the intent as comment
    // val (requested: Seq[Target], invalid: Seq[String]) =
    val (requested, invalid) = targets.map { t =>
      determineRequestedTarget(t) match {
        case Left(name) => name
        case Right(target) => target
      }
    }.partition(_.isInstanceOf[Target])

    (requested.asInstanceOf[Seq[Target]], invalid.asInstanceOf[Seq[String]])
  }

  class ExecutedTarget(
      /** The executed target. */
      val target: Target,
      /** An Id specific for this execution request. */
      val requestId: Option[String],
      val targetContext: TargetContext) {
    require(target == targetContext.target)
  }

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
  def preorderedDependenciesForest(request: Seq[Target],
                                   execProgress: Option[ExecProgress] = None,
                                   skipExec: Boolean = false,
                                   requestId: Option[String] = None,
                                   dependencyTrace: List[Target] = List(),
                                   depth: Int = 0,
                                   treePrinter: Option[(Int, Target) => Unit] = None)(implicit project: Project): Array[ExecutedTarget] =
    request.toArray.flatMap { req =>
      preorderedDependenciesTree(
        curTarget = req,
        execProgress = execProgress,
        skipExec = skipExec,
        requestId = requestId,
        dependencyTrace = dependencyTrace,
        depth = depth,
        treePrinter = treePrinter
      )
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

    val log = curTarget.project.log

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

    // Execute prerequisites
    val dependencies: List[Target] = try {
      curTarget.project.prerequisites(target = curTarget, searchInAllProjects = true)
    } catch {
      case e: UnsupportedSchemeException =>
        val ex = new UnsupportedSchemeException("Unsupported Scheme in dependencies of target: " +
          formatTarget(curTarget) + ". " + e.getMessage)
        ex.buildScript = e.buildScript
        throw ex
    }
    log.log(LogLevel.Debug, "Dependencies of " + formatTarget(curTarget) + ": " +
      (if (dependencies.isEmpty) "<none>" else dependencies.map(formatTarget(_)).mkString(" ~ ")))

    // All direct dependencies share the same request id.
    // Later we can identify them and check, if they were up-to-date.
    val resolveDirectDepsRequestId = Some(UUID.randomUUID.toString)

    val subDepTrace = curTarget :: dependencyTrace

    val executedDependencies: Array[ExecutedTarget] = dependencies.flatMap { dep =>
      preorderedDependenciesTree(dep,
        execProgress = execProgress, skipExec = skipExec, requestId = resolveDirectDepsRequestId,
        dependencyTrace = subDepTrace, depth = depth + 1, treePrinter = treePrinter)
    }.toArray

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

    val ctx: TargetContext = if (skipExec) {
      // already known as up-to-date
      new TargetContextImpl(curTarget, 0, Seq())

    } else {
      // not skipped execution, determine if dependencies were up-to-date

      log.log(LogLevel.Debug, "===> Current execution: " + formatTarget(curTarget) +
        " -> requested by: " + trace + " <===")

      log.log(LogLevel.Debug, "Executed dependency count: " + executedDependencies.size);

      log.log(LogLevel.Debug, "Request-ID used for dependencies: " + resolveDirectDepsRequestId)
      val directDepsExecuted = executedDependencies.filter(_.requestId == resolveDirectDepsRequestId)

      lazy val depsLastModified: Long = dependenciesLastModified(directDepsExecuted)
      val ctx = new TargetContextImpl(curTarget, depsLastModified, directDepsExecuted.map(_.targetContext))
      if (!directDepsExecuted.isEmpty)
        log.log(LogLevel.Debug, s"Dependencies have last modified value '${depsLastModified}': " + directDepsExecuted.map { d => formatTarget(d.target) }.mkString(","))

      val needsToRun: Boolean = curTarget.targetFile match {
        case Some(file) =>
          // file target
          if (!file.exists) {
            curTarget.project.log.log(LogLevel.Debug, s"""Target file "${file}" does not exists.""")
            true
          } else if (file.lastModified < depsLastModified) {
            curTarget.project.log.log(LogLevel.Debug, s"""Target file "${file}" is older (${file.lastModified}) then dependencies (${depsLastModified}).""")
            true
          } else
            false

        case None if curTarget.action == null =>
          // phony target but just a collector of dependencies
          ctx.targetLastModified = depsLastModified
          false

        // THIS IS THE WRONG PLACE - WE NEED TO EXTRACT ALSO THE ATTACHTED FILE & CO
        //        case None if curTarget.isCacheable =>
        //          // We will create a persistent up-to-date checker on behalf of the target.
        //          val checker = persistenceUpToDateChecker(ctx)
        //          if (checker.checkUpToDate(checker.createStateMap)) {
        //            // Checher reports up-to-dateness, so take the lastModified and return "needs-not-to-run"
        //            val stateLastModified = checker.stateFile.lastModified
        //            ctx.targetLastModified = stateLastModified
        //            log.log(LogLevel.Debug, s"Cacheable phony target reports an up-to-date cached lastModified: ${stateLastModified}")
        //            false
        //          } else {
        //            // Checker says no, so it needs to run
        //            log.log(LogLevel.Debug, s"Cacheable phony target ")
        //            true
        //          }

        case None =>
          // ensure, that the persistent state gets erased, whenever a non-cacheble phony target runs
          if (curTarget.isEvictCache) {
            curTarget.project.log.log(LogLevel.Debug, s"""Target "${curTarget.name}" will evict the target state cache now.""")
            dropAllCacheState(curTarget.project)
          }
          // phony target, have to run it always. Any laziness is up to it implementation
          curTarget.project.log.log(LogLevel.Debug, s"""Target "${curTarget.name}" is phony and needs to run (if not cached).""")
          true
      }

      if (!needsToRun)
        log.log(LogLevel.Debug, "Target '" + formatTarget(curTarget) + "' does not need to run.")

      //      // Print State
      //      execProgress.map { state =>
      //        val progress = (state.currentNr, state.maxCount) match {
      //          case (c, m) if (c > 0 && m > 0) =>
      //            val p = (c - 1) * 100 / m
      //            fPercent("[" + math.min(100, math.max(0, p)) + "%]")
      //          case (c, m) => "[" + c + "/" + m + "]"
      //        }
      //
      //        val ft = if (dependencyTrace.isEmpty) { fMainTarget _ } else { fTarget _ }
      //        val prefix = if (needsToRun) " Executing target: " else " Skipping target:  "
      //        val level = if (needsToRun || dependencyTrace.isEmpty) LogLevel.Info else LogLevel.Debug
      //        log.log(level, progress + prefix + ft(formatTarget(curTarget)))
      //
      //        state.currentNr += 1
      //      }

      val progressPrefix = execProgress match {
        case Some(state) =>
          val progress = (state.currentNr, state.maxCount) match {
            case (c, m) if (c > 0 && m > 0) =>
              val p = (c - 1) * 100 / m
              fPercent("[" + math.min(100, math.max(0, p)) + "%] ")
            case (c, m) => "[" + c + "/" + m + "] "
          }
          state.currentNr += 1
          progress
        case _ => ""
      }
      val colorTarget = if (dependencyTrace.isEmpty) { fMainTarget _ } else { fTarget _ }

      if (!needsToRun) {
        val level = if (dependencyTrace.isEmpty) LogLevel.Info else LogLevel.Debug
        log.log(level, progressPrefix + "Skipping target:  " + colorTarget(formatTarget(curTarget)))

      } else {
        curTarget.action match {
          case null =>
            // Additional sanity check
            if (!curTarget.phony) {
              val ex = new ProjectConfigurationException(s"""Target "${curTarget.name}" has no defined execution. Don't know how to create or update file "${curTarget.file}".""")
              ex.buildScript = Some(curTarget.project.projectFile)
              ex.targetName = Some(curTarget.name)
              throw ex
            }
            log.log(LogLevel.Debug, progressPrefix + "Skipping target:  " + colorTarget(formatTarget(curTarget)))
            log.log(LogLevel.Debug, "Nothing to execute (no action defined) for target: " + formatTarget(curTarget))
          case exec =>
            WithinTargetExecution.set(new WithinTargetExecution {
              override def targetContext: TargetContext = ctx
              override def directDepsTargetContexts: Seq[TargetContext] = ctx.directDepsTargetContexts
            })
            try {

              // if state is Some(_), it is already check to be up-to-date
              val cachedState: Option[CachedState] =
                if (curTarget.isCacheable) loadOrDropCachedState(ctx)
                else None

              cachedState match {
                case Some(cache) =>
                  log.log(LogLevel.Debug, progressPrefix + "Skipping cached target: " + colorTarget(formatTarget(curTarget)))
                  ctx.start
                  ctx.targetLastModified = cachedState.get.targetLastModified
                  cache.attachedFiles.foreach { file =>
                    ctx.attachFile(file)
                  }
                  ctx.end

                case None =>
                  val level = if (curTarget.isTransparentExec) LogLevel.Debug else LogLevel.Info
                  log.log(level, progressPrefix + "Executing target: " + colorTarget(formatTarget(curTarget)))
                  ctx.start
                  exec.apply(ctx)
                  ctx.end
                  log.log(LogLevel.Debug, s"Executed target '${formatTarget(curTarget)}' in ${ctx.execDurationMSec} msec")

                  // update persistent cache
                  if (curTarget.isCacheable) writeCachedState(ctx)
              }

              ctx.targetLastModified match {
                case Some(lm) =>
                  log.log(LogLevel.Debug, s"The context of target '${formatTarget(curTarget)}' reports a last modified value of '${lm}'. Request-ID: ${requestId}")
                case _ =>
              }

              ctx.attachedFiles match {
                case Seq() =>
                case files =>
                  log.log(LogLevel.Debug, s"The context of target '${formatTarget(curTarget)}' has ${files.size} attached files")
              }

            } catch {
              case e: TargetAware =>
                ctx.end
                if (e.targetName.isEmpty)
                  e.targetName = Some(formatTarget(curTarget))
                log.log(LogLevel.Error, s"Execution of target '${formatTarget(curTarget)}' aborted after ${ctx.execDurationMSec} msec with errors.\n${e.getMessage}")
                throw e
              case e: Throwable =>
                ctx.end
                val ex = new ExecutionFailedException(s"Execution of target ${formatTarget(curTarget)} failed with an exception: ${e.getClass.getName}.\n${e.getMessage}", e.getCause, s"Execution of target ${formatTarget(curTarget)} failed with an exception: ${e.getClass.getName}.\n${e.getLocalizedMessage}")
                ex.buildScript = Some(curTarget.project.projectFile)
                ex.targetName = Some(formatTarget(curTarget))
                log.log(LogLevel.Error, s"Execution of target '${formatTarget(curTarget)}' aborted after ${ctx.execDurationMSec} msec with errors: ${e.getMessage}")
                throw ex
            } finally {
              WithinTargetExecution.remove
            }
        }
      }

      ctx
    }

    executedDependencies ++ Array(
      new ExecutedTarget(
        target = curTarget,
        requestId = requestId,
        targetContext = ctx
      )
    )

  }

  private case class CachedState(targetLastModified: Long, attachedFiles: Seq[File])

  private def loadOrDropCachedState(ctx: TargetContext): Option[CachedState] = {
    // TODO: check same prerequisites, check same fileDependencies, check same lastModified of fileDependencies, check same lastModified
    // TODO: if all is same, return cached values

    ctx.project.log.log(LogLevel.Debug, "Checking execution state of target: " + ctx.name)

    var cachedPrerequisitesLastModified: Option[Long] = None
    var cachedFileDependencies: Set[File] = Set()
    var cachedPrerequisites: Seq[String] = Seq()
    var cachedTargetLastModified: Option[Long] = None
    var cachedAttachedFiles: Seq[File] = Seq()

    val stateDir = Path(".sbuild/scala/" + ctx.target.project.projectFile.getName + "/cache")(ctx.project)
    val stateFile = new File(stateDir, ctx.name.replaceFirst("^phony:", ""))
    if (!stateFile.exists) {
      ctx.project.log.log(LogLevel.Debug, s"""No execution state file for target "${ctx.name}" exists.""")
      return None
    }

    var mode = ""

    val source = Source.fromFile(stateFile)
    def closeAndDrop(reason: => String) {
      ctx.project.log.log(LogLevel.Debug, s"""Execution state file for target "${ctx.name}" exists, but is not up-to-date. Reason: ${reason}""")
      source.close
      stateFile.delete
    }

    source.getLines.foreach(line =>
      if (line.startsWith("[")) {
        mode = line
      } else {
        mode match {
          case "[prerequisitesLastModified]" =>
            cachedPrerequisitesLastModified = Try(line.toLong).toOption

          case "[prerequisites]" =>
            cachedPrerequisites ++= Seq(line)

          case "[fileDependencies]" =>
            cachedFileDependencies ++= Set(new File(line))

          case "[attachedFiles]" =>
            cachedAttachedFiles ++= Seq(new File(line))

          case "[targetLastModified]" =>
            cachedTargetLastModified = Try(line.toLong).toOption

          case unknownMode =>
            log.log(LogLevel.Warn, s"""Unexpected file format detected in file "${stateFile}". Dropping cached state of target "${ctx.name}".""")
            closeAndDrop("Unknown mode: " + unknownMode)
            return None
        }
      }
    )

    source.close

    if (cachedTargetLastModified.isEmpty) {
      closeAndDrop("Cached targetLastModified not defined.")
      return None
    }

    if (ctx.prerequisitesLastModified > cachedPrerequisitesLastModified.get) {
      closeAndDrop("prerequisitesLastModified do not match.")
      return None
    }

    if (ctx.prerequisites.size != cachedPrerequisites.size ||
      ctx.prerequisites.map { _.ref } != cachedPrerequisites) {
      closeAndDrop("prerequisites changed.")
      return None
    }

    val ctxFileDeps = ctx.fileDependencies.toSet
    if (ctxFileDeps.size != cachedFileDependencies.size ||
      ctxFileDeps != cachedFileDependencies) {
      closeAndDrop("fileDependencies changed.")
      return None
    }

    // TODO: also check existence of fileDependencies

    Some(CachedState(targetLastModified = cachedTargetLastModified.get, attachedFiles = cachedAttachedFiles))
  }

  private def writeCachedState(ctx: TargetContextImpl) {
    // TODO: robustness
    val stateDir = Path(".sbuild/scala/" + ctx.target.project.projectFile.getName + "/cache")(ctx.project)
    stateDir.mkdirs
    val stateFile = new File(stateDir, ctx.name.replaceFirst("^phony:", ""))
    val writer = new FileWriter(stateFile)

    writer.write("[prerequisitesLastModified]\n")
    writer.write(ctx.prerequisitesLastModified + "\n")

    writer.write("[prerequisites]\n")
    ctx.prerequisites.foreach { dep => writer.write(dep.ref + "\n") }

    writer.write("[fileDependencies]\n")
    ctx.fileDependencies.foreach { dep => writer.write(dep.getPath + "\n") }

    writer.write("[targetLastModified]\n")
    val targetLM = ctx.targetLastModified match {
      case Some(lm) => lm
      case _ =>
        ctx.endTime match {
          case Some(x) => x.getTime
          case None => System.currentTimeMillis
        }
    }
    writer.write(targetLM + "\n")

    writer.write("[attachedFiles]\n")
    ctx.attachedFiles.foreach { file => writer.write(file.getPath + "\n") }

    writer.close
    ctx.project.log.log(LogLevel.Debug, s"""Wrote execution cache state file for target "${ctx.name}" to ${stateFile}.""")

  }

  private def dropAllCacheState(project: Project) {
    val stateDir = Path(".sbuild/scala/" + project.projectFile.getName + "/cache")(project)
    Util.delete(stateDir)
  }

  def dependenciesLastModified(dependencies: Array[ExecutedTarget])(implicit project: Project): Long = {
    var lastModified: Long = 0
    def updateLastModified(lm: Long) {
      lastModified = math.max(lastModified, lm)
    }

    def now = System.currentTimeMillis

    dependencies.foreach { dep =>
      dep.target.targetFile match {
        case Some(file) if !file.exists =>
          log.log(LogLevel.Info, s"""The file "${file}" created by dependency "${formatTarget(dep.target)}" does no longer exists.""")
          updateLastModified(now)
        case Some(file) =>
          // file target and file exists, so we use its last modified
          updateLastModified(file.lastModified)
        case None =>
          // phony target, so we ask its target context 
          dep.targetContext.targetLastModified match {
            case Some(lm) =>
              // context has an associated last modified, which we will use
              updateLastModified(lm)
            case None =>
              // target context does not know something, so we fall back to a last modified of NOW
              updateLastModified(now)
          }
      }
    }
    lastModified
  }

  import org.fusesource.jansi.Ansi._
  import org.fusesource.jansi.Ansi.Color._

  def fPercent(text: => String) = ansi.fgBright(CYAN).a(text).reset
  def fTarget(text: => String) = ansi.fg(GREEN).a(text).reset
  def fMainTarget(text: => String) = ansi.fg(GREEN).bold.a(text).reset
  def fOk(text: => String) = ansi.fgBright(GREEN).a(text).reset
  def fError(text: => String) = ansi.fgBright(RED).a(text).reset
  def fErrorEmph(text: => String) = ansi.fgBright(RED).bold.a(text).reset

}
