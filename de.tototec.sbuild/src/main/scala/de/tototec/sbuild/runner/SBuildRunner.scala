package de.tototec.sbuild.runner

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.PrintStream
import java.lang.reflect.InvocationTargetException
import java.util.Date
import scala.collection.JavaConverters._
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

/**
 * SBuild command line application. '''API is not stable!'''
 */
class SBuildRunner {

  private[runner] var verbose = false

  private var log: SBuildLogger = new SBuildConsoleLogger(LogLevel.info)

  /**
   * Create a new build file.
   * If a file with the same name exists in the stubDir, then this one will be copied, else a build file with one target will be created.
   * If the file already exists, it will throw an [[de.tototec.sbuild.SBuildException]].
   *
   */
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

    val cache = new Cache()

    val targetsToCheck = projects.flatMap { p =>
      p.targets.values.filterNot(_.isImplicit)
    }
    // Console.println("About to check targets: "+targetsToCheck.mkString(", "))
    var errors: Seq[(Target, String)] = Seq()
    targetsToCheck.foreach { target =>
      log.log(LogLevel.Info, "Checking target: " + formatTarget(target)(baseProject))
      try {
        cache.fillTreeRecursive(target, Nil)
        log.log(LogLevel.Info, "  \t" + fOk("OK"))
      } catch {
        case e: SBuildException =>
          log.log(LogLevel.Info, "  \t" + fError("FAILED: " + e.getMessage))
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
    val targets = project.targets.values.filterNot(_.isImplicit)
    val cacheable = targets.filter(_.isCacheable)
    val evict = targets.filter(_.isEvictCache)

    if (evict.isEmpty && !cacheable.isEmpty) {
      val msg = s"""Project ${formatProject(project)}" contains ${cacheable.size} cacheable target, but does not declare any target with "evictCache"."""
      if (printWarning)
        project.log.log(LogLevel.Warn, msg)
      Some(msg)
    } else None
  }

  /**
   * Run the SBuild (command line) application with the given arguments.
   *
   * @return The exit value, `0` means no errors.
   */
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

    if (config.justClean || config.justCleanRecursive) {
      def cleanStateDir(dir: File, recursive: Boolean): Boolean = {
        var success = true
        val stateDir = new File(dir, ".sbuild")
        if (stateDir.exists && stateDir.isDirectory) {
          log.log(LogLevel.Info, "Deleting " + stateDir.getPath())
          success = Util.delete(stateDir) && success
        }
        if (recursive) {
          val files = dir.listFiles
          if (files != null) files.map { file =>
            if (file.isDirectory) {
              success = cleanStateDir(file, recursive) && success
            }
          }
        }
        success
      }
      val baseDir = new File(new File(".").getAbsoluteFile.toURI.normalize)
      if (cleanStateDir(baseDir, config.justCleanRecursive)) return 0 else return 1
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
    config.defines.asScala foreach {
      case (key, value) => project.addProperty(key, value)
    }

    projectReader.readProject(project, projectFile)

    val additionalProjects = config.additionalBuildfiles.map { buildfile =>
      project.findModule(buildfile) match {
        case None =>
          // Create and add new module and copy configs
          val module = project.findOrCreateModule(new File(buildfile).getAbsolutePath, copyProperties = false)
          config.defines.asScala foreach {
            case (key, value) => module.addProperty(key, value)
          }
          module

        case Some(module) => module // Module already defined
      }
    }

    // Now, that we loaded all projects, we can release some resources. 
    ProjectScript.dropCaches

    log.log(LogLevel.Debug, "Targets: \n" + project.targets.values.mkString("\n"))

    /**
     * Format a target relative to the base project.
     */
    def formatTargetsOf(baseProject: Project): String = {
      baseProject.targets.values.toSeq.filter(!_.isImplicit).sortBy(_.name).map { t =>
        formatTarget(t)(project) + " \t" + (t.help match {
          case null => ""
          case s: String => s
        })
      }.mkString("\n")
    }

    // Format listing of target and return
    if (config.listTargets || config.listTargetsRecursive) {
      val projectsToList = if (config.listTargetsRecursive) {
        project.projectPool.projects
      } else {
        Seq(project) ++ additionalProjects
      }
      val out = projectsToList.sortWith(projectSorter(project) _).map { p => formatTargetsOf(p) }
      log.log(LogLevel.Info, out.mkString("\n\n"))
      // early exit
      return 0
    }

    if (config.listModules) {
      val moduleNames = project.projectPool.projects.sortWith(projectSorter(project) _).map {
        p => formatProject(p)(project)
      }
      log.log(LogLevel.Info, moduleNames.mkString("\n"))
      return 0
    }

    // Check targets requested from cmdline an throw a exception, if invalid targets were requested
    val (requested: Seq[Target], invalid: Seq[String]) = determineRequestedTargets(config.params.asScala)(project)
    if (!invalid.isEmpty) {
      throw new TargetNotFoundException("Invalid target" + (if (invalid.size > 1) "s" else "") + " requested: " + invalid.mkString(", ") + ".");
    }
    val targets = requested

    // The dependencyTree will be populated by the treePrinter, in case it was requested on commandline

    class DependencyTree {
      private var dependencyTree = List[(Int, Target)]()

      def addNode(depth: Int, node: Target): Unit = dependencyTree = (depth -> node) :: dependencyTree

      def format(showGoUp: Boolean = true): String = {
        var lastDepth = 0
        var lastShown = Map[Int, Target]()
        val lines = dependencyTree.reverse.map {
          case (depth, target) =>

            var prefix = if (lastDepth > depth && depth > 0) {
              List.fill(depth - 1)("  ").mkString + "  (" + formatTarget(lastShown(depth - 1))(project) + ")\n"
            } else ""

            lastDepth = depth
            lastShown += (depth -> target)
            val line = prefix + List.fill(depth)("  ").mkString + "  " + formatTarget(target)(project)

            line
        }
        lines.mkString("\n")
      }
    }

    val depTree = if (config.showDependencyTree) {
      Some(new DependencyTree())
    } else None

    val treePrinter: Option[(Int, Target) => Unit] = depTree.map { t => t.addNode _ }

    val cache = new Cache()

    // The execution plan (chain) will be evaluated on first need
    lazy val chain: Seq[ExecutedTarget] = {
      if (!targets.isEmpty && !config.noProgress) {
        log.log(LogLevel.Info, "Calculating dependency tree...")
      }
      val chain = preorderedDependenciesForest(targets, skipExec = true, treePrinter = treePrinter, cache = cache)(project)
      log.log(LogLevel.Debug, "Target Dependency Cache: " + cache.cached.map {
        case (t, d) => "\n  " + formatTarget(t)(project) + " -> " + d.map {
          dep => formatTarget(dep)(project)
        }.mkString(", ")
      })
      chain
    }

    depTree.foreach { t =>
      // trigger chain
      chain
      // print output
      log.log(LogLevel.Info, "Dependency tree:\n" + t.format())
      // early exit
      return 0
    }

    // Execution plan
    def execPlan(chain: Seq[ExecutedTarget]) = {
      var line = 0
      var plan: List[String] = "Execution plan:" :: Nil

      def preorderDepthFirst(nodes: Seq[ExecutedTarget]): Unit = nodes.foreach { node =>
        node.dependencies match {
          case Seq() =>
            line += 1
            plan = ("  " + line + ". " + formatTarget(node.target)(project)) :: plan
          case deps =>
            preorderDepthFirst(deps)
        }
      }

      preorderDepthFirst(chain)
      plan.reverse.mkString("\n")
    }

    if (config.showExecutionPlan) {
      log.log(LogLevel.Info, execPlan(chain))
      // early exit
      return 0
    } else {
      log.log(LogLevel.Debug, execPlan(chain))
    }

    if (config.check || config.checkRecusive) {

      val projectsToCheck = if (config.checkRecusive) {
        project.projectPool.projects
      } else {
        Seq(project) ++ additionalProjects
      }

      val projects = projectsToCheck.sortWith(projectSorter(project) _)
      val errors = checkTargets(projects)(project)

      if (!errors.isEmpty) log.log(LogLevel.Error, s"Found the following ${fError(errors.size.toString)} problems:")
      errors.foreach {
        case (target, message) =>
          log.log(LogLevel.Error, fError(formatTarget(target)(project) + ": " + message).toString)
      }

      // also check for possible caching problems
      projects.foreach { p => checkCacheableTargets(p, printWarning = true)(project) }

      // When errors, then return with 1 else with 0
      return if (errors.isEmpty) 0 else 1
    }

    // force evaluation of lazy val chain, if required, and switch afterwards from bootstrap to execution time benchmarking.
    val execProgress =
      if (config.noProgress) None
      else Some(new ExecProgress(maxCount = chain.foldLeft(0) { (a, b) => a + b.treeSize }))

    val executionStart = System.currentTimeMillis
    val bootstrapTime = executionStart - bootstrapStart

    if (!targets.isEmpty && !config.noProgress) {
      log.log(LogLevel.Info, fPercent("[0%]") + " Executing...")
    }

    preorderedDependenciesForest(targets, execProgress = execProgress, cache = cache)(project)
    if (!targets.isEmpty && !config.noProgress) {
      log.log(LogLevel.Info, fPercent("[100%]") + " Execution finished. SBuild init time: " + bootstrapTime +
        " msec, Execution time: " + (System.currentTimeMillis - executionStart) + " msec")
    }

    log.log(LogLevel.Debug, "Finished")
    // return with 0, indicating no errors
    0
  }

  /**
   * Determine the requested target for the given input string.
   */
  def determineRequestedTarget(target: String, searchInAllProjects: Boolean = false)(implicit project: Project): Option[Target] =
    project.findTarget(target, searchInAllProjects = searchInAllProjects) match {
      case Some(target) => Some(target)
      case None => TargetRef(target).explicitProto match {
        case None | Some("phony") | Some("file") => None
        case _ =>
          // A scheme handler might be able to resolve this thing
          Some(project.createTarget(TargetRef(target)))
      }
    }

  def determineRequestedTargets(targets: Seq[String])(implicit project: Project): (Seq[Target], Seq[String]) = {

    // The compile will throw a warning here, so we use the erasure version and keep the intent as comment
    // val (requested: Seq[Target], invalid: Seq[String]) =
    val (requested, invalid) = targets.map { t =>
      determineRequestedTarget(t) match {
        case None => t
        case Some(target) => target
      }
    }.partition(_.isInstanceOf[Target])

    (requested.asInstanceOf[Seq[Target]], invalid.asInstanceOf[Seq[String]])
  }

  class ExecutedTarget(
      val targetContext: TargetContext,
      val dependencies: Seq[ExecutedTarget]) {
    def target = targetContext.target
    val treeSize: Int = dependencies.foldLeft(1) { (a, b) => a + b.treeSize }
    def linearized: Seq[ExecutedTarget] = dependencies.flatMap { et => et.linearized } ++ Seq(this)
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
                                   dependencyTrace: List[Target] = List(),
                                   depth: Int = 0,
                                   treePrinter: Option[(Int, Target) => Unit] = None,
                                   cache: Cache = new Cache())(implicit project: Project): Seq[ExecutedTarget] =
    request.map { req =>
      preorderedDependenciesTree(
        curTarget = req,
        execProgress = execProgress,
        skipExec = skipExec,
        dependencyTrace = dependencyTrace,
        depth = depth,
        treePrinter = treePrinter,
        cache = cache
      )
    }

  /**
   * This cache automatically resolves and caches dependencies of targets.
   *
   * It is assumed, that the involved projects are completely initialized and no new target will appear after this cache is active.
   */
  class Cache {
    private var depTrees: Map[Target, Seq[Target]] = Map()

    def cached: Map[Target, Seq[Target]] = depTrees

    /**
     * Return the direct dependencies of the given target.
     *
     * If this Cache already contains a cached result, that one will be returned.
     * Else, the dependencies will be computed through [[de.tototec.sbuild.Project#prerequisites]].
     *
     * If the parameter `callStack` is not `Nil`, the call stack including the given target will be checked for cycles.
     * If a cycle is detected, a [[de.tototec.sbuild.ProjectConfigurationException]] will be thrown.
     *
     * @throws ProjectConfigurationException If cycles are detected.
     * @throws UnsupportedSchemeException If an unsupported scheme was used in any of the targets.
     */
    def targetDeps(target: Target, dependencyTrace: List[Target] = Nil)(implicit project: Project): Seq[Target] = {
      // check for cycles
      dependencyTrace.find(dep => dep == target).map { cycle =>
        val ex = new ProjectConfigurationException("Cycles in dependency chain detected for: " + formatTarget(cycle) +
          ". The dependency chain: " + (target :: dependencyTrace).reverse.map(d => formatTarget(d)).mkString(" -> "))
        ex.buildScript = Some(cycle.project.projectFile)
        throw ex
      }

      depTrees.get(target) match {
        case Some(deps) => deps
        case None =>
          try {
            val deps = target.project.prerequisites(target = target, searchInAllProjects = true)
            depTrees += (target -> deps)
            deps
          } catch {
            case e: UnsupportedSchemeException =>
              val ex = new UnsupportedSchemeException("Unsupported Scheme in dependencies of target: " +
                formatTarget(target) + ". " + e.getMessage)
              ex.buildScript = e.buildScript
              ex.targetName = Some(formatTarget(target))
              throw ex
            case e: TargetAware if e.targetName == None =>
              e.targetName = Some(formatTarget(target))
              throw e
          }
      }
    }

    /**
     * Fills this cache by evaluating the given target and all its transitive dependencies.
     *
     * Internally, the method [[de.tototec.sbuild.runner.SBuildRunner.Cache#targetDeps]] is used.
     *
     * @throws ProjectConfigurationException If cycles are detected.
     * @throws UnsupportedSchemeException If an unsupported scheme was used in any of the targets.
     */
    def fillTreeRecursive(target: Target, parents: List[Target] = Nil)(implicit project: Project) {
      targetDeps(target, parents).foreach { dep => fillTreeRecursive(dep, target :: parents) }
    }

  }

  /**
   * Visit each target of tree `node` deep-first.
   *
   * If parameter `skipExec` is `true`, the associated actions will not executed.
   * If `skipExec` is `false`, for each target the up-to-date state will be evaluated,
   * and if the target is no up-to-date, the associated action will be executed.
   */
  def preorderedDependenciesTree(curTarget: Target,
                                 execProgress: Option[ExecProgress] = None,
                                 skipExec: Boolean = false,
                                 dependencyTrace: List[Target] = Nil,
                                 depth: Int = 0,
                                 treePrinter: Option[(Int, Target) => Unit] = None,
                                 cache: Cache = new Cache())(implicit project: Project): ExecutedTarget = {

    val log = curTarget.project.log

    treePrinter match {
      case Some(printFunc) => printFunc(depth, curTarget)
      case _ =>
    }

    val dependencies: Seq[Target] = cache.targetDeps(curTarget, dependencyTrace)

    log.log(LogLevel.Debug, "Dependencies of " + formatTarget(curTarget) + ": " +
      (if (dependencies.isEmpty) "<none>" else dependencies.map(formatTarget(_)).mkString(" ~ ")))

    val executedDependencies: Seq[ExecutedTarget] = dependencies.map { dep =>
      preorderedDependenciesTree(
        curTarget = dep,
        execProgress = execProgress,
        skipExec = skipExec,
        dependencyTrace = curTarget :: dependencyTrace,
        depth = depth + 1,
        treePrinter = treePrinter,
        cache = cache)
    }

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

      lazy val depsLastModified: Long = dependenciesLastModified(executedDependencies)
      val ctx = new TargetContextImpl(curTarget, depsLastModified, executedDependencies.map(_.targetContext))
      if (!executedDependencies.isEmpty)
        log.log(LogLevel.Debug, s"Dependencies have last modified value '${depsLastModified}': " + executedDependencies.map { d => formatTarget(d.target) }.mkString(","))

      val needsToRun: Boolean = curTarget.targetFile match {
        case Some(file) =>
          // file target
          if (!file.exists) {
            curTarget.project.log.log(LogLevel.Debug, s"""Target file "${file}" does not exists.""")
            true
          } else {
            val fileLastModified = file.lastModified
            if (fileLastModified < depsLastModified) {
              // On Linux, Oracle JVM always reports only seconds file time stamp,
              // even if file system supports more fine grained time stamps (e.g. ext4 supports nanoseconds)
              // So, it can happen, that files we just wrote seems to be older than targets, which reported "NOW" as their lastModified.
              curTarget.project.log.log(LogLevel.Debug, s"""Target file "${file}" is older (${fileLastModified}) then dependencies (${depsLastModified}).""")
              val diff = depsLastModified - fileLastModified
              if (diff < 1000 && fileLastModified % 1000 == 0 && System.getProperty("os.name").toLowerCase.contains("linux")) {
                curTarget.project.log.log(LogLevel.Debug, s"""Assuming up-to-dateness. Target file "${file}" is only ${diff} msec older, which might be caused by files system limitations or Oracle Java limitations (e.g. for ext4).""")
                false
              } else true

            } else false
          }
        case None if curTarget.action == null =>
          // phony target but just a collector of dependencies
          ctx.targetLastModified = depsLastModified
          val files = ctx.fileDependencies
          if (!files.isEmpty) {
            log.log(LogLevel.Debug, s"Attaching ${files.size} files of dependencies to empty phony target.")
            ctx.attachFileWithoutLastModifiedCheck(files)
          }
          false

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
                  ctx.attachFileWithoutLastModifiedCheck(cache.attachedFiles)
                  ctx.end

                case None =>
                  val level = if (curTarget.isTransparentExec) LogLevel.Debug else LogLevel.Info
                  log.log(level, progressPrefix + "Executing target: " + colorTarget(formatTarget(curTarget)))
                  if (curTarget.help != null && curTarget.help.trim != "")
                    log.log(level, progressPrefix + curTarget.help)
                  ctx.start
                  exec.apply(ctx)
                  ctx.end
                  log.log(LogLevel.Debug, s"Executed target '${formatTarget(curTarget)}' in ${ctx.execDurationMSec} msec")

                  // update persistent cache
                  if (curTarget.isCacheable) writeCachedState(ctx)
              }

              ctx.targetLastModified match {
                case Some(lm) =>
                  log.log(LogLevel.Debug, s"The context of target '${formatTarget(curTarget)}' reports a last modified value of '${lm}'.")
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
                log.log(LogLevel.Debug, s"Execution of target '${formatTarget(curTarget)}' aborted after ${ctx.execDurationMSec} msec with errors.\n${e.getMessage}", e)
                throw e
              case e: Throwable =>
                ctx.end
                val ex = new ExecutionFailedException(s"Execution of target ${formatTarget(curTarget)} failed with an exception: ${e.getClass.getName}.\n${e.getMessage}", e.getCause, s"Execution of target ${formatTarget(curTarget)} failed with an exception: ${e.getClass.getName}.\n${e.getLocalizedMessage}")
                ex.buildScript = Some(curTarget.project.projectFile)
                ex.targetName = Some(formatTarget(curTarget))
                log.log(LogLevel.Debug, s"Execution of target '${formatTarget(curTarget)}' aborted after ${ctx.execDurationMSec} msec with errors: ${e.getMessage}", e)
                throw ex
            } finally {
              WithinTargetExecution.remove
            }
        }
      }

      ctx
    }

    new ExecutedTarget(targetContext = ctx, dependencies = executedDependencies)

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

  def dependenciesLastModified(dependencies: Seq[ExecutedTarget])(implicit project: Project): Long = {
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
