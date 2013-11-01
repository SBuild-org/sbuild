package de.tototec.sbuild

import java.io.File
import de.tototec.sbuild.execute.TargetExecutor
import de.tototec.sbuild.internal.WithinTargetExecution

/**
 * EXPERIMENTAL API - Resolve TargetRefs.
 * 
 * This API may be removed in later versions in favour of plugins.
 */
object ResolveFiles {

  private[this] val log = Logger[ResolveFiles.type]

  def apply(targetRefs: TargetRefs)(implicit project: Project): Seq[File] = {

    WithinTargetExecution.get match {
      case null =>
        // ensure, we don't get executed within an target
        lazy val targetExecutor = new TargetExecutor(project.monitor, monitorConfig = TargetExecutor.MonitorConfig(topLevelSkipped = CmdlineMonitor.Verbose))

        log.debug("ResolveFiles request: " + targetRefs)

        val targetRefFiles = targetRefs.targetRefs.flatMap { targetRef =>
          project.determineRequestedTarget(targetRef, searchInAllProjects = true, supportCamelCaseShortCuts = false) match {
            case None =>
              // not found
              // if an existing file, then proceed.
              targetRef.explicitProto match {
                case None | Some("file") if targetRef.explicitProject == None && Path(targetRef.nameWithoutProto).exists =>
                  val fileRef = Path(targetRef.nameWithoutProto)
                  log.debug("Resolved to a local file reference: " + fileRef)
                  Seq(fileRef)
                case _ =>
                  throw new TargetNotFoundException(s"""Could not found target with name "${targetRef}" in project ${project.projectFile}.""")
              }

            case Some(target) =>
              log.debug("About to resolve target: " + target)
              val executedTarget = targetExecutor.preorderedDependenciesTree(curTarget = target)

              project.monitor.info(CmdlineMonitor.Verbose, "Resolved target '" + target + "' to: " + executedTarget)
              executedTarget.targetContext.targetFiles

          }
        }

        log.debug("Resolved files:\n  - " + targetRefFiles.mkString("\n  - "))

        targetRefFiles

      case _ =>
        val ex = InvalidApiUsageException.localized("'ResolveFiles' can only be used outside an exec block of a target.")
        ex.buildScript = Some(project.projectFile)
        throw ex
    }
  }
}