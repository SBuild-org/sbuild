package de.tototec.sbuild.execute

import scala.collection.parallel.ForkJoinTaskSupport
import scala.concurrent.Lock
import scala.concurrent.forkjoin.ForkJoinPool
import org.fusesource.jansi.Ansi.Color.CYAN
import org.fusesource.jansi.Ansi.Color.GREEN
import org.fusesource.jansi.Ansi.Color.RED
import org.fusesource.jansi.Ansi.ansi
import de.tototec.sbuild.CmdlineMonitor
import de.tototec.sbuild.ExecutionFailedException
import de.tototec.sbuild.Logger
import de.tototec.sbuild.ProjectConfigurationException
import de.tototec.sbuild.Target
import de.tototec.sbuild.TargetAware
import de.tototec.sbuild.TargetContext
import de.tototec.sbuild.TargetContextImpl
import de.tototec.sbuild.UnsupportedSchemeException
import de.tototec.sbuild.internal.WithinTargetExecution
import scala.collection.immutable.HashSet
import scala.collection.immutable.SortedSet

object TargetExecutor {

  case class MonitorConfig(
    // showPercent: Boolean = true,
    executing: CmdlineMonitor.OutputMode = CmdlineMonitor.Default,
    topLevelSkipped: CmdlineMonitor.OutputMode = CmdlineMonitor.Default,
    subLevelSkipped: CmdlineMonitor.OutputMode = CmdlineMonitor.Verbose // finished: Option[LogLevel] = Some(LogLevel.Debug))
    )

  //  case class ProcessConfig(parallelJobs: Option[Int] = None)

  /**
   * Track the current progress.
   */
  trait ExecProgress {
    def addToCurrentNr(addToCurrentNr: Int)
    //    def currentNr: Int
    def format: String
  }
  
  class MutableExecProgress(private[this] var maxCount: Int, private[this] var _currentNr: Int = 1) extends ExecProgress {
    def currentNr = _currentNr
    override def addToCurrentNr(addToCurrentNr: Int): Unit = synchronized { _currentNr += addToCurrentNr }
    override def format: String = if (_currentNr > 0 && maxCount > 0) {
      val p = (_currentNr - 1) * 100 / maxCount
      fPercent("[" + math.min(100, math.max(0, p)) + "%] ").toString
    } else {
      // fPercent("[" + _currentNr + "/" + maxCount + "] ").toString
      fPercent("[??] ").toString
    }
  }

  /**
   * This cache automatically resolves and caches dependencies of targets.
   *
   * It is assumed, that the involved projects are completely initialized and no new target will appear after this cache is active.
   */
  class DependencyCache() {
    private[this] val log = Logger[DependencyCache]

    private var depTrees: Map[Target, Seq[Seq[Target]]] = Map()

    def cached: Map[Target, Seq[Seq[Target]]] = synchronized { depTrees }

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
    def targetDeps(target: Target, dependencyTrace: List[Target] = Nil): Seq[Seq[Target]] = {
      dependencyTrace match {
        case Nil => // nothing to check
        case dependencyTrace =>
          log.trace("Checking for dependency cycles: " + target.formatRelativeToBaseProject)
          // check for cycles
          dependencyTrace.find(dep => dep == target).map { cycle =>
            val ex = new ProjectConfigurationException("Cycles in dependency chain detected for: " + cycle.formatRelativeToBaseProject +
              ". The dependency chain: " + (target :: dependencyTrace).reverse.map(_.formatRelativeToBaseProject).mkString(" -> "))
            ex.buildScript = Some(cycle.project.projectFile)
            throw ex
          }
      }

      synchronized {
        depTrees.get(target) match {
          case Some(deps) =>
            log.trace("Reusing cached dependencies of: " + target.formatRelativeToBaseProject)
            deps
          case None =>
            try {
              val deps = target.project.prerequisitesGrouped(target = target, searchInAllProjects = true)
              log.debug("Evaluated dependencies of: " + target.formatRelativeToBaseProject + " to: " + deps.map(_.map(_.formatRelativeToBaseProject)) + " base project: " + target.project.baseProject)
              depTrees += (target -> deps)
              deps
            } catch {
              case e: UnsupportedSchemeException =>
                val ex = new UnsupportedSchemeException("Unsupported Scheme in dependencies of target: " +
                  target.formatRelativeToBaseProject + ". " + e.getMessage)
                ex.buildScript = e.buildScript
                ex.targetName = Some(target.formatRelativeToBaseProject)
                throw ex
              case e: TargetAware if e.targetName == None =>
                e.targetName = Some(target.formatRelativeToBaseProject)
                throw e
            }
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
    def fillTreeRecursive(target: Target, parents: List[Target] = Nil): Unit = {
      log.trace("About to fill dependency tree recusivly for target: " + target.formatRelativeToBaseProject)
      targetDeps(target, parents).foreach { group =>
        group.foreach { dep =>
          fillTreeRecursive(dep, target :: parents)
        }
      }
    }

  }

  class KeepGoing() {
    private[this] val log = Logger[KeepGoing]

    private[this] var _failedTargets: Seq[(Target, Throwable)] = Seq()
    private[this] var _skippedTargets: Seq[Target] = Seq()

    def hasFailed(target: Target): Boolean = _failedTargets.exists { case (t, _) => t.eq(target) }
    def getError(target: Target): Option[Throwable] = _failedTargets.find { case (t, _) => t.eq(target) }.map(_._2)

    def hasSkipped(target: Target): Boolean = _skippedTargets.exists(_.eq(target))

    def markFailed(target: Target, error: Throwable): Unit = synchronized { _failedTargets ++= Seq(target -> error) }
    def markSkipped(target: Target) = _skippedTargets.find(_.eq(target)).getOrElse(synchronized { _skippedTargets ++= Seq(target) })

    def failedTargets: Seq[(Target, Throwable)] = _failedTargets
    def skippedTargets: Seq[Target] = _skippedTargets

  }

  import org.fusesource.jansi.Ansi._
  import org.fusesource.jansi.Ansi.Color._

  // It seems, under windows bright colors are not displayed correctly
  private val isWindows = System.getProperty("os.name").toLowerCase().contains("win")

  private def fPercent(text: => String) =
    if (isWindows) ansi.fg(CYAN).a(text).reset
    else ansi.fgBright(CYAN).a(text).reset
  private def fTarget(text: => String) = ansi.fg(GREEN).a(text).reset
  private def fMainTarget(text: => String) = ansi.fg(GREEN).bold.a(text).reset
  private def fOk(text: => String) = ansi.fgBright(GREEN).a(text).reset
  private def fError(text: => String) =
    if (isWindows) ansi.fg(RED).a(text).reset
    else ansi.fgBright(RED).a(text).reset
  private def fErrorEmph(text: => String) =
    if (isWindows) ansi.fg(RED).bold.a(text).reset
    else ansi.fgBright(RED).bold.a(text).reset

}

class TargetExecutor(monitor: CmdlineMonitor,
                     persistentTargetCache: PersistentTargetCache = new PersistentTargetCache(),
                     monitorConfig: TargetExecutor.MonitorConfig = TargetExecutor.MonitorConfig() // processConfig: TargetExecutor.ProcessConfig = TargetExecutor.ProcessConfig()
                     ) {

  private[this] val log = Logger[TargetExecutor]

  import TargetExecutor._

  /**
   * Visit a forest of targets, each target of parameter `request` is the root of a tree.
   * Each tree will search deep-first. If parameter `skipExec` is `true`, the associated actions will not executed.
   * If `skipExec` is `false`, for each target the up-to-date state will be evaluated,
   * and if the target is no up-to-date, the associated action will be executed.
   */
  def preorderedDependenciesForest(request: Seq[Target],
                                   execProgress: Option[TargetExecutor.ExecProgress] = None,
                                   skipExec: Boolean = false,
                                   dependencyTrace: List[Target] = List(),
                                   depth: Int = 0,
                                   treePrinter: Option[(Int, Target) => Unit] = None,
                                   dependencyCache: DependencyCache = new DependencyCache(),
                                   transientTargetCache: Option[TransientTargetCache] = None,
                                   treeParallelExecContext: Option[ParallelExecContext] = None,
                                   keepGoing: Option[KeepGoing] = None): Seq[ExecutedTarget] =
    request.map { req =>
      preorderedDependenciesTree(
        curTarget = req,
        execProgress = execProgress,
        skipExec = skipExec,
        dependencyTrace = dependencyTrace,
        depth = depth,
        treePrinter = treePrinter,
        dependencyCache = dependencyCache,
        transientTargetCache = transientTargetCache,
        parallelExecContext = treeParallelExecContext,
        keepGoing = keepGoing
      )
    }

  /**
   * Visit each target of tree `node` deep-first.
   *
   * If parameter `skipExec` is `true`, the associated actions will not executed.
   * If `skipExec` is `false`, for each target the up-to-date state will be evaluated,
   * and if the target is no up-to-date, the associated action will be executed.
   */
  def preorderedDependenciesTree(curTarget: Target,
                                 execProgress: Option[TargetExecutor.ExecProgress] = None,
                                 skipExec: Boolean = false,
                                 dependencyTrace: List[Target] = Nil,
                                 depth: Int = 0,
                                 treePrinter: Option[(Int, Target) => Unit] = None,
                                 dependencyCache: DependencyCache = new DependencyCache(),
                                 transientTargetCache: Option[TransientTargetCache] = None,
                                 parallelExecContext: Option[ParallelExecContext] = None,
                                 keepGoing: Option[KeepGoing] = None): ExecutedTarget = {

    def inner: ExecutedTarget = {

      val curTargetFormatted = curTarget.formatRelativeToBaseProject

      treePrinter match {
        case Some(printFunc) => printFunc(depth, curTarget)
        case _ =>
      }

      // if curTarget is already cached, use the cached result
      transientTargetCache.flatMap(_.get(curTarget)).map { cachedExecutedContext =>
        // push progress forward by the size of the cached result
        execProgress.map(_.addToCurrentNr(cachedExecutedContext.treeSize))
        return cachedExecutedContext
      }

      val dependencies: Seq[Seq[Target]] = dependencyCache.targetDeps(curTarget, dependencyTrace)

      val executedDependencies: Seq[ExecutedTarget] = if (dependencies.isEmpty) Seq()
      else {
        log.trace("Processing dependencies of target: " + curTargetFormatted + " which are: " +
          dependencies.map(_.map(_.formatRelativeToBaseProject).mkString(" ~ ")).mkString(" ~~ "))

        val executedDependencies: Seq[ExecutedTarget] = parallelExecContext match {
          case None =>
            dependencies.flatten.map { dep =>
              preorderedDependenciesTree(
                curTarget = dep,
                execProgress = execProgress,
                skipExec = skipExec,
                dependencyTrace = curTarget :: dependencyTrace,
                depth = depth + 1,
                treePrinter = treePrinter,
                dependencyCache = dependencyCache,
                transientTargetCache = transientTargetCache,
                parallelExecContext = parallelExecContext,
                keepGoing = keepGoing)
            }
          case Some(parCtx) =>

            dependencies.map { group =>

              // val parDeps = collection.parallel.mutable.ParArray(dependencies: _*)
              val parDeps = collection.parallel.immutable.ParVector(group: _*)
              parDeps.tasksupport = parCtx.taskSupport

              val result = parDeps.map { dep =>
                preorderedDependenciesTree(
                  curTarget = dep,
                  execProgress = execProgress,
                  skipExec = skipExec,
                  dependencyTrace = curTarget :: dependencyTrace,
                  depth = depth + 1,
                  treePrinter = treePrinter,
                  dependencyCache = dependencyCache,
                  transientTargetCache = transientTargetCache,
                  parallelExecContext = parallelExecContext,
                  keepGoing = keepGoing)
              }

              result.seq.toSeq

            }.flatten
        }

        log.trace("Processing of dependencies finished for target: " + curTargetFormatted)
        executedDependencies
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
          x.map { "\n" + prefix + _.formatRelativeToBaseProject }.mkString
      }

      case class ExecBag(ctx: TargetContext, resultState: ExecutedTarget.ResultState)

      def calcProgressPrefix = execProgress match {
        case Some(state) => state.format
        case None => ""
      }
      val colorTarget = if (dependencyTrace.isEmpty) { fMainTarget _ } else { fTarget _ }

      val execBag: ExecBag = if (skipExec) {
        // already known as up-to-date

        ExecBag(ctx = new TargetContextImpl(curTarget, 0, Seq()), ExecutedTarget.SkippedUpToDate)

      } else {
        // not skipped execution, determine if dependencies were up-to-date

        val someDepsFailed = if (keepGoing.isDefined) {
          log.trace("Check resultState of dependencies: " + executedDependencies.map(d => d.target.formatRelativeToBaseProject + ": " + d.resultState).mkString("\n  ", "\n  ", ""))
          val someDepsFailed = !executedDependencies.forall(_.resultState.successful)
          if (someDepsFailed) {
            log.debug("Keep-going mode: Some dependencies failed earlier, but we continue but skip the current target: " + curTargetFormatted)
          }
          someDepsFailed
        } else false
        val (failedEarlier, skippedEarlier) = keepGoing match {
          case Some(kg) => (kg.hasFailed(curTarget), kg.hasSkipped(curTarget))
          case None => (false, false)
        }

        log.debug("===> Current execution: " + curTargetFormatted +
          " -> requested by: " + trace + " <===")

        log.debug("Executed dependency count: " + executedDependencies.size);

        lazy val depsLastModified: Long = if (failedEarlier || someDepsFailed || skippedEarlier) 0 else dependenciesLastModified(executedDependencies)
        val ctx = new TargetContextImpl(curTarget, depsLastModified, executedDependencies.map(_.targetContext))
        if (!executedDependencies.isEmpty)
          log.debug(s"Dependencies have last modified value '${depsLastModified}': " + executedDependencies.map(_.target.formatRelativeToBaseProject).mkString(","))

        case class NeedsToRun(needsToRun: Boolean, lastModifiedTime: Long = 0)

        val needsToRun: NeedsToRun = if (failedEarlier || someDepsFailed || skippedEarlier) NeedsToRun(false) else curTarget.targetFile match {
          case Some(file) =>
            // file target
            if (!file.exists) {
              log.debug(s"""Target file "${file}" does not exists.""")
              NeedsToRun(true)
            } else {
              val fileLastModified = file.lastModified
              val now = System.currentTimeMillis
              if (fileLastModified > now) {
                // TODO: consider an offset of about 3 seconds (as Make does)
                monitor.warn(s"""Modification time of file "${file}" is in the future. Up-to-date checks may be incorrect.""")
              }
              if (fileLastModified < depsLastModified) {
                // On Linux, Oracle JVM always reports only seconds file time stamp,
                // even if file system supports more fine grained time stamps (e.g. ext4 supports nanoseconds)
                // So, it can happen, that files we just wrote seems to be older than targets, which reported "NOW" as their lastModified.
                log.debug(s"""Target file "${file}" is older (${fileLastModified}) than dependencies (${depsLastModified}).""")
                val diff = depsLastModified - fileLastModified
                if (diff < 1000 && fileLastModified % 1000 == 0 && System.getProperty("os.name").toLowerCase.contains("linux")) {
                  log.debug(s"""Assuming up-to-dateness. Target file "${file}" is only ${diff} msec older, which might be caused by files system limitations or Oracle Java limitations (e.g. for ext4).""")
                  NeedsToRun(false, fileLastModified)
                } else NeedsToRun(true, fileLastModified)

              } else NeedsToRun(false, fileLastModified)
            }
          case None if curTarget.action == null =>
            // phony target but just a collector of dependencies
            ctx.targetLastModified = depsLastModified
            val files = ctx.fileDependencies
            if (!files.isEmpty) {
              log.debug(s"Attaching ${files.size} files of dependencies to empty phony target.")
              ctx.attachFileWithoutLastModifiedCheck(files)
            }
            NeedsToRun(false)

          case None =>
            // ensure, that the persistent state gets erased, whenever a non-cacheble phony target runs
            curTarget.evictsCache.map { cacheName =>
              log.debug(s"""Target "${curTargetFormatted}" will evict the target state cache with name "${cacheName}" now.""")
              persistentTargetCache.dropCacheState(curTarget.project, cacheName)
            }
            // phony target, have to run it always. Any laziness is up to it implementation
            log.debug(s"""Target "${curTargetFormatted}" is phony and needs to run (if not cached).""")
            NeedsToRun(true)
        }

        if (!needsToRun.needsToRun)
          log.debug(s"""Target "${curTargetFormatted}" does not need to run.""")

        val resultState: ExecutedTarget.ResultState =
          if (failedEarlier) ExecutedTarget.SkippedFailedEarlier
          else if (skippedEarlier) ExecutedTarget.SkippedDependenciesFailed
          else if (someDepsFailed) {
            keepGoing.map(_.markSkipped(curTarget))
            ExecutedTarget.SkippedDependenciesFailed
          } else if (!needsToRun.needsToRun) ExecutedTarget.SkippedUpToDate
          else curTarget.action match {
            case null =>
              // Additional sanity check
              if (curTarget.phony) ExecutedTarget.SkippedEmptyExec
              else {
                val ex = new ProjectConfigurationException(s"""Target "${curTarget.name}" has no defined execution. Don't know how to create or update file "${curTarget.file}".""")
                ex.buildScript = Some(curTarget.project.projectFile)
                ex.targetName = Some(curTarget.name)
                keepGoing match {
                  case None => throw ex
                  case Some(kg) =>
                    kg.markFailed(curTarget, ex)
                    ExecutedTarget.Failed
                }
              }
            case exec =>
              WithinTargetExecution.set(new WithinTargetExecution {
                override def targetContext: TargetContext = ctx
                override def directDepsTargetContexts: Seq[TargetContext] = ctx.directDepsTargetContexts
              })
              try {

                // if state is Some(_), it is already check to be up-to-date
                val cachedState: Option[persistentTargetCache.CachedState] =
                  if (curTarget.isCacheable) persistentTargetCache.loadOrDropCachedState(ctx)
                  else None

                val resultState: ExecutedTarget.ResultState = cachedState match {
                  case Some(cache) =>
                    ctx.start
                    ctx.targetLastModified = cachedState.get.targetLastModified
                    ctx.attachFileWithoutLastModifiedCheck(cache.attachedFiles)
                    ctx.end
                    ExecutedTarget.SkippedPersistentCachedUpToDate

                  case None =>
                    if (!curTarget.isSideeffectFree) transientTargetCache.map(_.evict)
                    val level = if (curTarget.isTransparentExec) CmdlineMonitor.Verbose else monitorConfig.executing
                    val progressPrefix = calcProgressPrefix
                    monitor.info(level, progressPrefix + "Executing target: " + colorTarget(curTargetFormatted))
                    log.debug("Executing Target: " + curTargetFormatted)
                    if (curTarget.help != null && curTarget.help.trim != "")
                      monitor.info(level, progressPrefix + curTarget.help)
                    ctx.start
                    exec.apply(ctx)
                    ctx.end

                    // update persistent cache
                    if (curTarget.isCacheable) persistentTargetCache.writeCachedState(ctx)

                    ExecutedTarget.Success
                }

                ctx.targetLastModified match {
                  case Some(lm) => log.debug(s"The context of target '${curTargetFormatted}' reports a last modified value of '${lm}'.")
                  case None =>
                }

                ctx.attachedFiles match {
                  case Seq() =>
                  case files => log.debug(s"The context of target '${curTargetFormatted}' has ${files.size} attached files")
                }

                if (!curTarget.phony && needsToRun.lastModifiedTime > 0)
                  curTarget.targetFile.find(f => f.lastModified == needsToRun.lastModifiedTime).map { file =>
                    val msg = s"Outcome of target ${curTargetFormatted} looks out-of-date, as the timestamp hasn't changed."
                    monitor.warn(CmdlineMonitor.Default, msg)
                    log.warn(msg)
                  }

                resultState

              } catch {
                case e: TargetAware =>
                  ctx.end
                  log.debug("Caught an exception while executing target: " + curTargetFormatted, e)
                  if (e.targetName.isEmpty)
                    e.targetName = Some(curTargetFormatted)
                  monitor.info(CmdlineMonitor.Verbose, s"Execution of target '${curTargetFormatted}' aborted after ${ctx.execDurationMSec} msec with errors.\n${e.getMessage}")
                  monitor.showStackTrace(CmdlineMonitor.Verbose, e)

                  keepGoing match {
                    case Some(kg) =>
                      kg.markFailed(curTarget, e)
                      ExecutedTarget.Failed
                    case None =>
                      monitor.info(monitorConfig.executing, calcProgressPrefix + fError("Failed target: ") +
                        colorTarget(curTarget.formatRelativeToBaseProject) + fError(" after " + ctx.execDurationMSec + " msec"))
                      throw e
                  }
                case e: Throwable =>
                  ctx.end
                  log.debug("Caught an exception while executing target: " + curTargetFormatted, e)
                  val ex = new ExecutionFailedException(
                    s"Execution of target ${curTargetFormatted} failed with an exception: ${e.getClass.getName}.\n${e.getMessage}",
                    e,
                    s"Execution of target ${curTargetFormatted} failed with an exception: ${e.getClass.getName}.\n${e.getLocalizedMessage}")
                  ex.buildScript = Some(curTarget.project.projectFile)
                  ex.targetName = Some(curTarget.formatRelativeToBaseProject)
                  monitor.info(CmdlineMonitor.Verbose, s"Execution of target '${curTargetFormatted}' aborted after ${ctx.execDurationMSec} msec with errors: ${e.getMessage}")
                  monitor.showStackTrace(CmdlineMonitor.Verbose, e)

                  keepGoing match {
                    case Some(kg) =>
                      kg.markFailed(curTarget, e)
                      ExecutedTarget.Failed
                    case None =>
                      monitor.info(monitorConfig.executing, calcProgressPrefix + fError("Failed target: ") +
                        colorTarget(curTarget.formatRelativeToBaseProject) + fError(" after " + ctx.execDurationMSec + " msec"))
                      throw e
                  }
              } finally {
                WithinTargetExecution.remove
              }
          }

        log.trace("Result state: " + resultState + " for target " + curTargetFormatted)
        ExecBag(ctx, resultState)
      }

      execProgress.map(_.addToCurrentNr(1))

      log.debug("Target: " + curTargetFormatted + " => " + execBag.resultState + " after + " + execBag.ctx.execDurationMSec + " msec")

      if (!skipExec) {
        def skipLevel = if (dependencyTrace.isEmpty) monitorConfig.topLevelSkipped else monitorConfig.subLevelSkipped
        def execDurationMSec = execBag.ctx.execDurationMSec

        execBag.resultState match {
          case ExecutedTarget.Success =>
            if (parallelExecContext.isDefined && !curTarget.isTransparentExec) {
              // when parallel, print some finish message
              monitor.info(monitorConfig.executing, calcProgressPrefix + "Finished target: " + colorTarget(curTargetFormatted) + " after " + execDurationMSec + " msec")
            }

          case ExecutedTarget.Failed =>
            monitor.info(monitorConfig.executing, calcProgressPrefix + fError("Failed target: ") + colorTarget(curTargetFormatted) + fError(" after " + execDurationMSec + " msec"))

          case ExecutedTarget.SkippedDependenciesFailed =>
            monitor.info(skipLevel, calcProgressPrefix + fError("Skipped unsatisfied target: ") + colorTarget(curTargetFormatted))
          case ExecutedTarget.SkippedFailedEarlier =>
            monitor.info(skipLevel, calcProgressPrefix + fError("Skipped perviously failed target: ") + colorTarget(curTargetFormatted))

          case t if t.successful && dependencyTrace.isEmpty => // not top level skipped
            monitor.info(monitorConfig.topLevelSkipped, calcProgressPrefix + "Finished target: " + colorTarget(curTargetFormatted))

          case ExecutedTarget.SkippedUpToDate =>
            monitor.info(skipLevel, calcProgressPrefix + "Skipped target: " + colorTarget(curTargetFormatted))
          case ExecutedTarget.SkippedEmptyExec =>
            monitor.info(skipLevel, calcProgressPrefix + "Skipped empty target: " + colorTarget(curTargetFormatted))
          case ExecutedTarget.SkippedPersistentCachedUpToDate =>
            monitor.info(skipLevel, calcProgressPrefix + "Skipped cached target: " + colorTarget(curTargetFormatted))

        }
      }

      val executedTarget = new ExecutedTarget(targetContext = execBag.ctx, dependencies = executedDependencies, resultState = execBag.resultState)

      if (execBag.resultState.successful && transientTargetCache.isDefined)
        transientTargetCache.get.cache(curTarget, executedTarget)

      executedTarget

    }

    parallelExecContext match {
      case None => inner

      case Some(parCtx) =>
        parCtx.lock(curTarget)
        try {
          inner
        } catch {
          case e: Throwable =>
            log.debug("Catched an exception in parallel executed targets.", e)
            val firstError = parCtx.getFirstError(e)
            // we need to stop the complete ForkJoinPool
            parCtx.pool.shutdownNow()
            throw firstError
        } finally {
          parCtx.unlock(curTarget)
        }
    }

  }

  def dependenciesLastModified(dependencies: Seq[ExecutedTarget]): Long = {
    val now = System.currentTimeMillis

    // TODO: consider optimization: escape calculation immediately after we reach NOW

    dependencies.foldLeft(0L) { (prevLastModified, dep) =>
      val modifiedAt: Long = dep.target.targetFile match {
        case Some(file) if !file.exists =>
          def msg = s"""The file "${file}" created by dependency "${dep.target.formatRelativeToBaseProject}" does no longer exists."""
          log.warn(msg)
          monitor.warn(msg)
          now
        case Some(file) =>
          // file target and file exists, so we use its last modified
          val lm = file.lastModified
          if (lm > now) {
            // TODO: consider an offset of about 3 seconds (as Make does)
            monitor.warn(s"""Modification time of file "${file}" is in the future. Up-to-date checks may be incorrect.""")
          }
          lm
        case None =>
          // phony target, so we ask its target context 
          dep.targetContext.targetLastModified match {
            case Some(lm) =>
              // context has an associated last modified, which we will use
              if (lm > now) {
                // TODO: consider an offset of about 3 seconds (as Make does)
                monitor.warn(s"""Reported modification time of target "${dep.targetContext.target.formatRelativeToBaseProject}" is in the future. Up-to-date checks may be incorrect.""")
              }
              lm
            case None =>
              // target context does not know something, so we fall back to a last modified of NOW
              now
          }
      }
      math.max(prevLastModified, modifiedAt)
    }
  }

}

