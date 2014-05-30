package de.tototec.sbuild

import java.io.File
import de.tototec.sbuild.execute.TargetExecutor
import de.tototec.sbuild.internal.WithinTargetExecution
import de.tototec.sbuild.internal.I18n

/**
 * EXPERIMENTAL API - Resolve TargetRefs.
 *
 * This API may be removed in later versions in favour of plugins.
 */
@deprecated("Do not use. Will be replaced by recursive resolve dependencies capabilities in plugins.", "0.6.0.9003")
object ResolveFiles {

  private[this] val log = Logger[ResolveFiles.type]
  private[this] val i18n = I18n[ResolveFiles.type]
  import i18n._

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

                  val msg = marktr("Could not find target with name \"{0}\" in project {1}.")
                  throw new TargetNotFoundException(notr(msg, targetRef, project.projectFile), null, tr(msg, targetRef, project.projectFile))
              }

            case Some(target) =>
              log.debug("About to resolve target: " + target)
              val executedTarget = targetExecutor.preorderedDependenciesTree(curTarget = target)

              project.monitor.info(CmdlineMonitor.Verbose, tr("Resolved target \"{0}\" to: {1}", target, executedTarget))
              executedTarget.targetContext.targetFiles

          }
        }

        log.debug("Resolved files:\n  - " + targetRefFiles.mkString("\n  - "))

        targetRefFiles

      case _ =>
        val msg = marktr("'ResolveFiles' can only be used outside an exec block of a target.")
        val ex = new InvalidApiUsageException(notr(msg, null, tr(msg)))
        ex.buildScript = Some(project.projectFile)
        throw ex
    }
  }
}