package de.tototec.sbuild.internal

import de.tototec.sbuild.TargetContext
import de.tototec.sbuild.InvalidApiUsageException
import de.tototec.sbuild.Project

trait WithinTargetExecution {
  def targetContext: TargetContext
  protected[sbuild] def directDepsTargetContexts: Seq[TargetContext]
}

object WithinTargetExecution extends ThreadLocal[WithinTargetExecution] {
  private[sbuild] override def remove: Unit = super.remove
  private[sbuild] override def set(withinTargetExecution: WithinTargetExecution): Unit = super.set(withinTargetExecution)

  /**
   * To use a WithinTargetExecution, one should use this method.
   */
  private[sbuild] def safeWithinTargetExecution[T](callingMethodName: String, project: Option[Project] = None)(doWith: WithinTargetExecution => T): T =
    get match {
      case null =>
        val msg = I18n.marktr("'{0}' can only be used inside an exec block of a target.")
        val ex = new InvalidApiUsageException(I18n.notr(msg, callingMethodName), null, I18n[WithinTargetExecution.type].tr(msg, callingMethodName))
        ex.buildScript = project.map(_.projectFile)
        throw ex

      case withinExecution => doWith(withinExecution)
    }

  //  private[sbuild] def safeTargetContext(callingMethodName: String, project: Option[Project] = None): TargetContext =
  //    safeWithinTargetExecution(callingMethodName, project)(_.targetContext)

}