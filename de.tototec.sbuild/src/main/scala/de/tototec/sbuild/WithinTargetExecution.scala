package de.tototec.sbuild

trait WithinTargetExecution {
  def targetContext: TargetContext
}

object WithinTargetExecution extends ThreadLocal[WithinTargetExecution] {
  private[sbuild] override def remove: Unit = super.remove
  private[sbuild] override def set(withinTargetExecution: WithinTargetExecution): Unit = super.set(withinTargetExecution)
}