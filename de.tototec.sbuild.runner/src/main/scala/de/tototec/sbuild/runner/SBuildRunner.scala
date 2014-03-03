package de.tototec.sbuild.runner

import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.FileReader
import java.io.PrintStream
import java.lang.reflect.InvocationTargetException
import java.util.Properties
import java.util.regex.Pattern
import scala.Array.canBuildFrom
import scala.Option.option2Iterable
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.collection.JavaConverters.mapAsScalaMapConverter
import org.fusesource.jansi.Ansi
import org.fusesource.jansi.Ansi.Color.CYAN
import org.fusesource.jansi.Ansi.Color.GREEN
import org.fusesource.jansi.Ansi.Color.RED
import org.fusesource.jansi.Ansi.ansi
import org.fusesource.jansi.AnsiConsole
import de.tototec.cmdoption.CmdOption
import de.tototec.cmdoption.CmdlineParser
import de.tototec.cmdoption.CmdlineParserException
import de.tototec.sbuild.BuildScriptAware
import de.tototec.sbuild.CmdlineMonitor
import de.tototec.sbuild.ExecutionFailedException
import de.tototec.sbuild.InvalidApiUsageException
import de.tototec.sbuild.Logger
import de.tototec.sbuild.OutputStreamCmdlineMonitor
import de.tototec.sbuild.Project
import de.tototec.sbuild.ProjectConfigurationException
import de.tototec.sbuild.ProjectReader
import de.tototec.sbuild.RichFile.toRichFile
import de.tototec.sbuild.SBuildException
import de.tototec.sbuild.SBuildVersion
import de.tototec.sbuild.Target
import de.tototec.sbuild.TargetAware
import de.tototec.sbuild.TargetNotFoundException
import de.tototec.sbuild.TargetRef
import de.tototec.sbuild.execute.InMemoryTransientTargetCache
import de.tototec.sbuild.execute.LoggingTransientTargetCache
import de.tototec.sbuild.execute.ParallelExecContext
import de.tototec.sbuild.execute.TargetExecutor
import de.tototec.sbuild.internal.BuildFileProject
import de.tototec.sbuild.internal.I18n
import de.tototec.sbuild.execute.ExecutedTarget

object SBuildRunner extends SBuildRunner {

  System.setProperty("http.agent", "SBuild/" + SBuildVersion.osgiVersion)

  def main(args: Array[String]) {

    AnsiConsole.systemInstall
    val retval = run(args)
    AnsiConsole.systemUninstall
    sys.exit(retval)
  }

}

/**
 * SBuild command line application. '''API is not stable!'''
 */
class SBuildRunner {

  private[this] val log = Logger[SBuildRunner]
  private[this] val i18n = I18n[SBuildRunner]
  import i18n._

  private var verbose = false

  private[this] var sbuildMonitor: CmdlineMonitor = new OutputStreamCmdlineMonitor(Console.out, CmdlineMonitor.Default)

  //  private val persistentTargetCache = new PersistentTargetCache()

  /**
   * Create a new build file.
   * If a file with the same name exists in the stubDir, then this one will be copied, else a build file with one target will be created.
   * If the file already exists, it will throw an [[de.tototec.sbuild.SBuildException]].
   *
   */
  def createSBuildStub(projectFile: File, stubDir: File) {
    if (projectFile.exists) {
      val msg = preparetr("File \"{0}\" already exists.", projectFile.getName)
      throw new SBuildException(msg.notr, null, msg.tr)
    }

    val sbuildStub = new File(stubDir, projectFile.getName) match {

      case stubFile if stubFile.exists =>
        val source = io.Source.fromFile(stubFile)
        val text = source.mkString
        source.close
        text

      case _ =>
        val className = projectFile.getName match {
          case name if name.endsWith(".scala") && name.head.isUpper =>
            name.substring(0, projectFile.getName.length - 6)
          case _ => "SBuild"
        }

        s"""|import de.tototec.sbuild._
            |
            |@version("${SBuildVersion.osgiVersion}")
            |class ${className}(implicit _project: Project) {
            |
            |  Target("phony:clean") exec {
            |    Path("target").deleteRecursive
            |  }
            |
            |  Target("phony:hello") help "Greet me" exec {
            |    println("Hello you")
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

  /**
   * Check all targets of the given projects for problems.
   *
   * Checked problems are:
   * - invalid target names
   * - missing dependencies
   * - cycles in dependencies
   * - missing scheme handlers
   *
   * @return A sequence of pairs of problematic target and a error message.
   */
  def checkTargets(projects: Seq[Project])(implicit baseProject: Project): Seq[(Target, String)] = {
    log.debug("About to check targets of project: " + projects.map(_.projectFile))

    val cache = new TargetExecutor.DependencyCache()

    val targetsToCheck = projects.flatMap { p =>
      p.targets
    }
    log.debug("targets to check: " + targetsToCheck.map(_.formatRelativeToBaseProject))
    // Console.println("About to check targets: "+targetsToCheck.mkString(", "))
    var errors: Seq[(Target, String)] = Seq()
    targetsToCheck.foreach { target =>
      sbuildMonitor.info(CmdlineMonitor.Default, tr("Checking target: ") + target.formatRelativeToBaseProject)
      try {
        cache.fillTreeRecursive(target, Nil)
        sbuildMonitor.info(CmdlineMonitor.Default, "  \t" + fOk(tr("OK")))
      } catch {
        case e: SBuildException =>
          sbuildMonitor.info(CmdlineMonitor.Default, "  \t" + fError(tr("FAILED: ") + e.getMessage))
          errors ++= Seq(target -> e.getMessage)
      }
    }
    errors
  }

  /**
   * Check a project for target definition with cacheable or evictCache property.
   * If cacheable targets are found but not at least one evictCache target, return a error message.
   */
  def checkCacheableTargets(project: Project, printWarning: Boolean)(implicit baseProject: Project): Option[String] = {
    val targets = project.targets
    val cacheable = targets.filter(_.isCacheable)
    val evict = targets.filter(_.evictsCache.isDefined)

    if (evict.isEmpty && !cacheable.isEmpty) {
      val msg = marktr("Project {0} contains {1} cacheable target, but does not declare any target with \"evictCache\".")
      if (printWarning)
        project.monitor.info(tr(msg, formatProject(project), cacheable.size))
      Some(notr(msg, formatProject(project), cacheable.size))
    } else None
  }

  private[this] def errorOutput(e: Throwable, msg: String = null) = {
    sbuildMonitor.info("")

    if (msg != null)
      sbuildMonitor.info(fError(msg).toString)

    if (e.isInstanceOf[BuildScriptAware] && e.asInstanceOf[BuildScriptAware].buildScript.isDefined)
      sbuildMonitor.info(fError(tr("Project: ")).toString + fErrorEmph(e.asInstanceOf[BuildScriptAware].buildScript.get.getPath).toString)

    if (e.isInstanceOf[TargetAware] && e.asInstanceOf[TargetAware].targetName.isDefined)
      sbuildMonitor.info(fError(tr("Target:  ")).toString + fErrorEmph(e.asInstanceOf[TargetAware].targetName.get).toString)

    sbuildMonitor.info(fError(tr("Details: ") + e.getLocalizedMessage).toString)

    if (e.getCause() != null && e.getCause().isInstanceOf[InvocationTargetException] && e.getCause().getCause() != null)
      sbuildMonitor.info(fError(tr("Reflective invokation message: ") + e.getCause().getCause().getLocalizedMessage()).toString())
  }

  private[this] def readAndApplyGlobal(config: Config) {
    val rcFile = new File(System.getProperty("user.home"), ".sbuildrc")
    if (!rcFile.exists()) sbuildMonitor.info(CmdlineMonitor.Verbose, tr("No global settings file found: {0}", rcFile))
    else {
      sbuildMonitor.info(CmdlineMonitor.Verbose, tr("About to read global settings file: {0}", rcFile))
      val props = new Properties()
      try {
        props.load(new BufferedReader(new FileReader(rcFile)))
      } catch {
        case e: Exception =>
          sbuildMonitor.error(tr("Could not read settings file \"{0}\"", rcFile.getPath))
          sbuildMonitor.showStackTrace(CmdlineMonitor.Verbose, e)
      }

      def readSetting(key: String, applyFunction: String => Unit) = try {
        props.getProperty(key) match {
          case null =>
          case value => applyFunction(value)
        }
      } catch {
        case e: Exception =>
          sbuildMonitor.error(tr("Could not read setting \"{0}\" from settings file \"{1}\".", key, rcFile.getPath))
          sbuildMonitor.showStackTrace(CmdlineMonitor.Verbose, e)
      }

      readSetting("jobs", jobCount => if (config.parallelJobs.isEmpty) config.parallelJobs = Some(jobCount.toInt))
    }
  }

  /**
   * Run the SBuild (command line) application with the given arguments.
   *
   * @return The exit value, `0` means no errors.
   */
  def run(args: Array[String]): Int = {
    val bootstrapStart = System.currentTimeMillis

    val aboutAndVersion = "SBuild " + SBuildVersion.version + " (c) 2011 - 2013, ToToTec GbR, Tobias Roeser"

    try {

      val config = new Config()
      val classpathConfig = new ClasspathConfig()
      val cmdlineConfig = new {
        @CmdOption(names = Array("--version"), isHelp = true, description = "Show SBuild version.")
        var showVersion = false

        @CmdOption(names = Array("--help", "-h"), isHelp = true, description = "Show this help screen.")
        var help = false

        @CmdOption(names = Array("--no-color"), description = "Disable colored output.")
        var noColor = false

        @CmdOption(names = Array("--no-global"), description = "Do not read global settings from <USER HOME>/.sbuildrc.")
        var noGlobal = false
      }
      val cp = new CmdlineParser(config, classpathConfig, cmdlineConfig)
      cp.setResourceBundle("de.tototec.sbuild.runner.Messages", getClass.getClassLoader())
      cp.setAboutLine(aboutAndVersion)
      cp.setProgramName("sbuild")

      cp.parse(args: _*)
      if (!cmdlineConfig.noGlobal) {
        readAndApplyGlobal(config)
      }

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

      if (config.justClean || config.justCleanRecursive) {
        def cleanStateDir(dir: File, recursive: Boolean) {
          val stateDir = new File(dir, ".sbuild")
          if (stateDir.exists && stateDir.isDirectory) {
            sbuildMonitor.info(CmdlineMonitor.Default, tr("Deleting {0}", stateDir.getPath()))
            stateDir.deleteRecursive
          }
          if (recursive) {
            val files = dir.listFiles
            if (files != null) files.map { file =>
              if (file.isDirectory) {
                cleanStateDir(file, recursive)
              }
            }
          }
        }
        val baseDir = new File(new File(".").getAbsoluteFile.toURI.normalize)
        cleanStateDir(baseDir, config.justCleanRecursive)
        return 0
      }

      run(config = config, classpathConfig = classpathConfig, bootstrapStart = bootstrapStart)

    } catch exceptionHandler(rethrowInVerboseMode = true)
  }

  // TODO: If we catch an exceptiona and know, that an older minimal SBuild version was requested (with @version) we could print a list of incompatible changes to the user. 
  def exceptionHandler(rethrowInVerboseMode: Boolean): PartialFunction[Throwable, Int] = {
    case e: CmdlineParserException =>
      errorOutput(e, tr("SBuild commandline was invalid. Please use --help for supported commandline options."))
      log.error("", e)
      if (rethrowInVerboseMode && verbose) throw e
      1
    case e: InvalidApiUsageException =>
      errorOutput(e, tr("SBuild detected a invalid usage of SBuild API. Please consult the API Refence Documentation at http://sbuild.tototec.de ."))
      log.error("", e)
      if (rethrowInVerboseMode && verbose) throw e
      1
    case e: ProjectConfigurationException =>
      errorOutput(e, tr("SBuild detected a failure in the project configuration or the build scripts."))
      log.error("", e)
      if (rethrowInVerboseMode && verbose) throw e
      1
    case e: TargetNotFoundException =>
      errorOutput(e, tr("SBuild failed because an invalid target was requested. For a list of available targets use --list-targets or --list-targets-recursive. Use --help for a list of other commandline options."))
      log.error("", e)
      if (rethrowInVerboseMode && verbose) throw e
      1
    case e: ExecutionFailedException =>
      errorOutput(e, tr("SBuild detected a failure in a target execution."))
      log.error("", e)
      if (rethrowInVerboseMode && verbose) throw e
      1
    case e: Exception =>
      errorOutput(e, tr("SBuild failed with an unexpected exception ({0}).", e.getClass.getSimpleName))
      log.error("", e)
      if (rethrowInVerboseMode && verbose) throw e
      1
  }

  /**
   * Sort projects by their fully qualified build file name.
   * The base project is always sorted to top position.
   */
  def projectSorter(baseProject: Project)(l: Project, r: Project): Boolean = (l, r) match {
    // ensure main project file is on top
    case (l, r) if l.eq(baseProject) => true
    case (l, r) if r.eq(baseProject) => false
    case (l, r) => l.projectFile.compareTo(r.projectFile) < 0
  }

  def compileAndLoadProjects(projectFile: File, config: Config, classpathConfig: ClasspathConfig): (BuildFileProject, Seq[Project]) = {
    val projectReader: ProjectReader = new SimpleProjectReader(
      classpathConfig = classpathConfig,
      monitor = sbuildMonitor,
      clean = config.clean,
      fileLocker = new FileLocker(),
      initialProperties = config.defines
    )
    val project = projectReader.readAndCreateProject(projectFile, Map(), None, Some(sbuildMonitor)).asInstanceOf[BuildFileProject]

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

    // Now, that we loaded all projects, we can release some resources. 
    ProjectScript.dropCaches

    (project, additionalProjects)
  }

  /**
   * Format all non-implicit targets of a project.
   * If the project is not the main/entry project, to project name will be included in the formatted name.
   */
  def formatTargetsOf(project: Project): Seq[String] =
    project.targets.sortBy(_.name).map { t =>
      t.formatRelativeToBaseProject + " \t" + (t.help match {
        case null => ""
        case s: String => s
      })
    }

  def formatPluginsOf(project: Project, includeUnused: Boolean): Seq[String] =
    project.registeredPlugins.
      filter { p => includeUnused || !p.instances.isEmpty }.
      map { p => s"${p.name} ${p.version}" }

  def run(config: Config, classpathConfig: ClasspathConfig, bootstrapStart: Long = System.currentTimeMillis): Int = {

    SBuildRunner.verbose = config.verbosity == CmdlineMonitor.Verbose
    sbuildMonitor = new OutputStreamCmdlineMonitor(Console.out, config.verbosity)

    val projectFile = new File(config.buildfile)

    if (config.createStub) {
      createSBuildStub(projectFile, new File(classpathConfig.sbuildHomeDir, "stub"))
      return 0
    }

    val outputAndExit = config.listModules || config.listTargets || config.listTargetsRecursive || config.listPlugins || config.searchTargets.isDefined

    sbuildMonitor.info(
      if (outputAndExit) CmdlineMonitor.Verbose else CmdlineMonitor.Default,
      tr("Initializing project..."))

    val (project, additionalProjects) = compileAndLoadProjects(projectFile, config, classpathConfig)
    log.debug("Targets: \n" + project.targets.map(_.formatRelativeToBaseProject).mkString("\n"))

    // Format listing of target and return
    if (config.listTargets || config.listTargetsRecursive || config.searchTargets.isDefined) {
      val recursive = config.listTargetsRecursive || config.searchTargets.isDefined
      val projectsToList =
        if (recursive) project.projectPool.projects
        else (project +: additionalProjects)

      val formattedTargets: Seq[Seq[String]] = projectsToList.sortWith(projectSorter(project) _).map { p => formatTargetsOf(p) }

      if (config.searchTargets.isDefined) {
        val highlighter =
          (if (isWindows) ansi.fg(RED).a("$0").reset
          else ansi.fgBright(RED).a("$0").reset).toString

        val pattern = Pattern.compile(config.searchTargets.get)
        val out = formattedTargets.flatten.collect {
          case t if pattern.matcher(t).find() => pattern.matcher(t).replaceAll(highlighter)
        }
        sbuildMonitor.info(out.mkString("\n"))
      } else {
        val out = formattedTargets.map { ts => ts.mkString("\n") }.mkString("\n\n")
        sbuildMonitor.info(out)
      }
      // early exit
      return 0
    }

    if (config.listModules) {
      val moduleNames = project.projectPool.projects.sortWith(projectSorter(project) _).map(p => formatProject(p))
      sbuildMonitor.info(moduleNames.mkString("\n"))
      return 0
    }

    if (config.listAvailablePlugins || config.listPlugins) {
      val plugins = formatPluginsOf(project, includeUnused = config.listAvailablePlugins)
      sbuildMonitor.info(plugins.mkString("\n"))
      return 0
    }

    // Check targets requested from cmdline an throw an exception, if invalid targets were requested
    val (requested: Seq[Target], invalid: Seq[String]) =
      determineRequestedTargets(targets = config.params.asScala, supportCamelCaseShortCuts = true)(project)
    if (!invalid.isEmpty) {
      throw new TargetNotFoundException("Invalid target" + (if (invalid.size > 1) "s" else "") + " requested: " + invalid.mkString(", ") + ".");
    }
    val targets = requested

    if (config.check || config.checkRecusive) {
      val projectsToCheck =
        if (config.checkRecusive) { project.projectPool.projects }
        else { project +: additionalProjects }

      val projects = projectsToCheck.sortWith(projectSorter(project) _)
      val errors = checkTargets(projects)(project)

      if (!errors.isEmpty) {
        sbuildMonitor.info(CmdlineMonitor.Default, tr("Found the following {0} problems:", fError(errors.size.toString)))
        errors.map {
          case (target, message) =>
            sbuildMonitor.info(CmdlineMonitor.Default, fError(target.formatRelativeToBaseProject + ": " + message).toString)
        }
      }

      // also check for possible caching problems
      projects.foreach { p => checkCacheableTargets(p, printWarning = true)(project) }

      // When errors, then return with 1 else with 0
      return if (errors.isEmpty) 0 else 1
    }

    val targetExecutor = new TargetExecutor(sbuildMonitor)
    val dependencyCache = new TargetExecutor.DependencyCache()

    if (config.showDependencyTree) {
      sbuildMonitor.info(CmdlineMonitor.Default, tr("Dependency tree:"))
      var lastDepth = 0
      var lastShown = Map[Int, Target]()
      targetExecutor.dependencyTreeWalker(targets, dependencyCache, onTargetBeforeDeps = {
        case Nil =>
        case target :: trace =>
          var depth = trace.size
          var prefix = if (lastDepth > depth && depth > 0) {
            List.fill(depth - 1)("  ").mkString + "(" + lastShown(depth - 1).formatRelativeToBaseProject + ")\n"
          } else ""

          lastDepth = depth
          lastShown += (depth -> target)
          val line = prefix + List.fill(depth)("  ").mkString + target.formatRelativeToBaseProject
          sbuildMonitor.info(line)
      })
      return 0
    }

    if (config.showExecutionPlan) {
      sbuildMonitor.info(CmdlineMonitor.Default, tr("Execution plan (not optimized):"))
      var line = 0
      targetExecutor.dependencyTreeWalker(targets, dependencyCache, onTargetAfterDeps = {
        case Nil =>
        case target :: _ =>
          line += 1
          sbuildMonitor.info(line + ". " + target.formatRelativeToBaseProject)
      })
      return 0
    }

    val parallelExecContext = config.parallelJobs.flatMap {
      case 1 => None
      case jobCount =>
        // Multiple jobs
        val explicitJobCount = jobCount match {
          case x if x > 1 => Some(x)
          case 0 => None
          case _ => None // TODO: this should be a config error
        }
        sbuildMonitor.info(CmdlineMonitor.Verbose,
          tr("Enabled parallel processing. Explicit parallel threads (None = nr of cpu cores): {0}", explicitJobCount.toString))
        Some(new ParallelExecContext(threadCount = explicitJobCount))
    }

    val bootstrapTime = System.currentTimeMillis - bootstrapStart
    log.debug("Bootstrap time in milliseconds: " + bootstrapTime)

    val localExceptionHandler: PartialFunction[Throwable, Int] =
      if (config.repeatAfterSec > 0) exceptionHandler(rethrowInVerboseMode = false)
      else { case t: Throwable => throw t }

    log.debug("Calculating count of executions...")
    val beforeMaxExecCount = System.currentTimeMillis
    val maxExecCount = targetExecutor.calcTotalExecTreeNodeCount(request = targets, dependencyCache = dependencyCache)
    log.debug("Calculated count of executions: " + maxExecCount + " after " + (System.currentTimeMillis - beforeMaxExecCount) + " msec")

    var repeat = true
    var lastRepeatStart = 0L
    while (repeat) {
      repeat = config.repeatAfterSec > 0
      if (repeat) {
        val nextStart = lastRepeatStart + (config.repeatAfterSec * 1000)
        nextStart - System.currentTimeMillis match {
          case msec if msec > 0 =>
            sbuildMonitor.info(CmdlineMonitor.Default, s"Repeating execution in ${msec} milliseconds...")
            Thread.sleep(msec)
          case _ =>
            sbuildMonitor.info(CmdlineMonitor.Default, s"Repeating execution...")
        }
      }
      lastRepeatStart = System.currentTimeMillis

      try {

        val execProgress =
          if (config.verbosity == CmdlineMonitor.Quiet) None
          else Some(new TargetExecutor.MutableExecProgress(maxCount = maxExecCount))

        val keepGoing = if (config.keepGoing) Some(new TargetExecutor.KeepGoing()) else None

        if (!targets.isEmpty) {
          sbuildMonitor.info(CmdlineMonitor.Default, fPercent("[0%]") + tr(" Executing..."))
          sbuildMonitor.info(CmdlineMonitor.Verbose, tr("Requested targets: ") + targets.map(_.formatRelativeToBaseProject).mkString(" ~ "))

          val cmdlineTargetsParallelCtx = if (config.parallelRequest) parallelExecContext else None

          val execResult = new targetExecutor.WithParallelExecContext(cmdlineTargetsParallelCtx).run(None) { withParCtx =>
            withParCtx.parallelMapper(Seq(targets)) { target =>
              targetExecutor.preorderedDependenciesTree(
                target,
                execProgress = execProgress,
                dependencyCache = dependencyCache,
                transientTargetCache = Some(new InMemoryTransientTargetCache() with LoggingTransientTargetCache),
                parallelExecContext = parallelExecContext,
                keepGoing = keepGoing
              )
            }
          }

          //          val execResult = targets.map { target =>
          //            targetExecutor.preorderedDependenciesTree(
          //              target,
          //              execProgress = execProgress,
          //              dependencyCache = dependencyCache,
          //              transientTargetCache = Some(new InMemoryTransientTargetCache() with LoggingTransientTargetCache),
          //              parallelExecContext = parallelExecContext,
          //              keepGoing = keepGoing
          //            )
          //          }

          execResult.filter(!_.resultState.successful) match {
            case Seq() =>
              sbuildMonitor.info(CmdlineMonitor.Default, fPercent("[100%]") +
                tr(" Execution finished. SBuild init time: {0} msec, Execution time: {1} msec",
                  bootstrapTime, (System.currentTimeMillis - lastRepeatStart)))
            case _ =>
              sbuildMonitor.info(CmdlineMonitor.Default, fPercent("[100%]") +
                fError(tr(" Execution failed. SBuild init time: {0} msec, Execution time: {1} msec",
                  bootstrapTime, (System.currentTimeMillis - lastRepeatStart))).toString)
              val msg = i18n.marktr("Some targets failed or were skipped because of unsatisfied dependencies: {0}{1}")
              val arg1 = keepGoing.toSeq.flatMap(_.failedTargets).map {
                case (t, ex) => "\n  FAILED  " + t.formatRelativeToBaseProject + ": " + ex.getLocalizedMessage()
              }.mkString
              val arg2 = keepGoing.toSeq.flatMap(_.skippedTargets).map("\n  SKIPPED " + _.formatRelativeToBaseProject).mkString
              val ex = new ExecutionFailedException(i18n.notr(msg, arg1, arg2), null, i18n.tr(msg, arg1, arg2))
              throw ex
            //                sbuildMonitor.error(CmdlineMonitor.Default, "The following targets failed:" +

          }

        }

      } catch localExceptionHandler
    }

    // sbuildMonitor.info(CmdlineMonitor.Verbose, "Finished")
    // return with 0, indicating no errors
    0
  }

  def determineRequestedTargets(targets: Seq[String], supportCamelCaseShortCuts: Boolean = false)(implicit project: Project): (Seq[Target], Seq[String]) = {

    // The compile will throw a warning here, so we use the erasure version and keep the intent as comment
    // val (requested: Seq[Target], invalid: Seq[String]) =
    val (requested, invalid) = targets.map { t =>
      project.determineRequestedTarget(targetRef = TargetRef(t), searchInAllProjects = false, supportCamelCaseShortCuts = supportCamelCaseShortCuts) match {
        case None => t
        case Some(target) => target
      }
    }.partition(_.isInstanceOf[Target])

    (requested.asInstanceOf[Seq[Target]], invalid.asInstanceOf[Seq[String]])
  }

  def formatProject(project: Project): String = project.baseProject match {
    case Some(baseProject) =>
      baseProject.projectDirectory.toURI.relativize(project.projectFile.toURI).getPath
    case None => project.projectFile.getName
  }

  import org.fusesource.jansi.Ansi._
  import org.fusesource.jansi.Ansi.Color._

  // It seems, under windows bright colors are not displayed correctly
  val isWindows = System.getProperty("os.name").toLowerCase().contains("win")

  def fPercent(text: => String) =
    if (isWindows) ansi.fg(CYAN).a(text).reset
    else ansi.fgBright(CYAN).a(text).reset
  def fTarget(text: => String) = ansi.fg(GREEN).a(text).reset
  def fMainTarget(text: => String) = ansi.fg(GREEN).bold.a(text).reset
  def fOk(text: => String) = ansi.fgBright(GREEN).a(text).reset
  def fError(text: => String) =
    if (isWindows) ansi.fg(RED).a(text).reset
    else ansi.fgBright(RED).a(text).reset
  def fErrorEmph(text: => String) =
    if (isWindows) ansi.fg(RED).bold.a(text).reset
    else ansi.fgBright(RED).bold.a(text).reset

}

