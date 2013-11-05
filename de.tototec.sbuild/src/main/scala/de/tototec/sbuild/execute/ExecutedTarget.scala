package de.tototec.sbuild.execute

import de.tototec.sbuild.TargetContext

object ExecutedTarget {

  sealed abstract class ResultState(val successful: Boolean, val wasUpToDate: Boolean)
  case object Success extends ResultState(true, false)
  case object SkippedUpToDate extends ResultState(true, true)
  case object SkippedPersistentCachedUpToDate extends ResultState(true, true)
  case object SkippedEmptyExec extends ResultState(true, true)
  case object Failed extends ResultState(false, false)
  case object SkippedFailedEarlier extends ResultState(false, false)
  case object SkippedDependenciesFailed extends ResultState(false, false)
}

/**
 * Tree-like structure containing the targetContext of an already executed target and the same for all of the targets dependencies.
 */
class ExecutedTarget(
    val targetContext: TargetContext,
    val dependencies: Seq[ExecutedTarget],
    val resultState: ExecutedTarget.ResultState) {
  def target = targetContext.target
  val treeSize: Int = dependencies.foldLeft(1) { (a, b) => a + b.treeSize }
  def linearized: Seq[ExecutedTarget] = dependencies.flatMap { et => et.linearized } ++ Seq(this)
}
