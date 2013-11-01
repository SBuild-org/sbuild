package de.tototec.sbuild.internal

import de.tototec.sbuild.TargetContext

trait WithinTargetExecution {
  def targetContext: TargetContext
  protected[sbuild] def directDepsTargetContexts: Seq[TargetContext]
}

object WithinTargetExecution extends ThreadLocal[WithinTargetExecution] {
  private[sbuild] override def remove: Unit = super.remove
  private[sbuild] override def set(withinTargetExecution: WithinTargetExecution): Unit = super.set(withinTargetExecution)
}