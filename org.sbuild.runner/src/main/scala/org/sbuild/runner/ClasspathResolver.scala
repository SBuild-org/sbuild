package org.sbuild.runner

import java.io.File
import org.sbuild.Project
import org.sbuild.Logger
import org.sbuild.OutputStreamCmdlineMonitor
import org.sbuild.execute.TargetExecutor
import org.sbuild.internal.BuildFileProject
import org.sbuild.Target
import org.sbuild.TargetRef
import org.sbuild.CmdlineMonitor
import org.sbuild.execute.InMemoryTransientTargetCache
import org.sbuild.execute.ParallelExecContext
import org.sbuild.internal.Bootstrapper

sealed abstract class CpTree(val pluginInfo: Option[LoadablePluginInfo], val childs: Seq[CpTree]) {
  def flatPath: Seq[File] = pluginInfo.toSeq.flatMap(_.files) ++ childs.flatMap(_.flatPath)
  override def toString() = getClass.getSimpleName() +
    "(pluginInfo=" + pluginInfo +
    ")"
}
object EmptyCpTree extends CpTree(None, Seq())
class LeafCpTree(pluginInfo: LoadablePluginInfo) extends CpTree(Some(pluginInfo), Seq())
class NodeCpTree(pluginInfo: LoadablePluginInfo, childs: Seq[CpTree]) extends CpTree(Some(pluginInfo), childs)

object ClasspathResolver {

  sealed trait CpEntry { def name: String }
  case class RawCpEntry(name: String) extends CpEntry
  case class ExtensionCpEntry(name: String) extends CpEntry

  case class ResolveRequest(includeEntries: Seq[String], classpathEntries: Seq[String])

  case class ResolvedClassPathInfo(includes: Map[String, Seq[File]], classpathTrees: Seq[CpTree]) {
    def flatClasspath: Seq[File] = classpathTrees.flatMap(_.flatPath)
  }
}

class ClasspathResolver(projectFile: File, outputMode: CmdlineMonitor.OutputMode, bootstrapper: Bootstrapper) extends Function1[ClasspathResolver.ResolveRequest, ClasspathResolver.ResolvedClassPathInfo] {
  import ClasspathResolver._

  private[this] val log = Logger[ClasspathResolver]

  override def apply(request: ResolveRequest): ResolvedClassPathInfo = {
    log.debug("About to resolve project contribution from @classpath and @include annotations for project: " + projectFile)
    log.debug("classpath: " + request.classpathEntries)
    log.debug("includes: " + request.includeEntries)
    if (request.classpathEntries.isEmpty && request.includeEntries.isEmpty) return ResolvedClassPathInfo(Map(), Seq())

    val cpEntries = request.classpathEntries.map {
      case x if x.startsWith("raw:") => RawCpEntry(x.substring(4))
      case x => ExtensionCpEntry(x)
    }

    // We want to use a customized monitor
    val resolveMonitor = new OutputStreamCmdlineMonitor(Console.out, mode = outputMode, messagePrefix = "(init) ")

    // We want to use a dedicated project in the init phase
    class ProjectInitProject extends BuildFileProject(_projectFile = projectFile, monitor = resolveMonitor)
    implicit val initProject: Project = new ProjectInitProject
    bootstrapper.applyToProject(initProject)
    

    //    val idxCpEntries = classpathTargets.zipWithIndex.map { case (l, r) => r -> l }
    val cpTargets = cpEntries.map {
      cpEntry => TargetRef(cpEntry.name)
    }

    val incTargets = request.includeEntries.map(TargetRef(_))
    val resolverTargetName = s"phony:@init:${projectFile}"
    val resolverTarget = Target(resolverTargetName) dependsOn cpTargets ~ incTargets

    val targetExecutor = new TargetExecutor(
      monitor = initProject.monitor,
      monitorConfig = TargetExecutor.MonitorConfig(
        executing = CmdlineMonitor.Verbose,
        topLevelSkipped = CmdlineMonitor.Verbose
      ))

    val dependencyCache = new TargetExecutor.DependencyCache()
    val maxCount = targetExecutor.calcTotalExecTreeNodeCount(request = Seq(resolverTarget), dependencyCache = dependencyCache)
    val execProgressOption = Some(new TargetExecutor.MutableExecProgress(maxCount = maxCount))

    val executedResolverTarget = targetExecutor.preorderedDependenciesTree(
      curTarget = resolverTarget,
      transientTargetCache = Some(new InMemoryTransientTargetCache()),
      dependencyCache = dependencyCache,
      execProgress = execProgressOption,
      parallelExecContext = Some(new ParallelExecContext(threadCount = None))
    )

    val files = executedResolverTarget.dependencies.map { d =>
      var name = d.target.name
      val files = d.targetContext.targetFiles
      name -> files
    }

    val filesMap = files.toMap

    // TODO: improve accuracy by unpacking resolved tree and split result into rawClasspath, include and plugin
    // TODO: check size of files seq and files map, should be both have same length

    val includes = request.includeEntries.map { name => name -> filesMap(name) }.toMap

    val cpTrees: Seq[CpTree] = cpEntries.map {
      case RawCpEntry(name) =>
        // raw entries can not specify additional dependencies
        new LeafCpTree(new LoadablePluginInfo(files = filesMap(name), raw = true))
      case ExtensionCpEntry(name) =>
        val pi = new LoadablePluginInfo(files = filesMap(name), raw = false)

        new VersionChecker().assertPluginVersion(pi, projectFile)

        pi.dependencies match {
          case Seq() => new LeafCpTree(pi)
          case deps =>
            val result = apply(ResolveRequest(includeEntries = Seq(), classpathEntries = deps))
            new NodeCpTree(pi, result.classpathTrees)
        }
    }

    ResolvedClassPathInfo(includes, cpTrees)

  }

}