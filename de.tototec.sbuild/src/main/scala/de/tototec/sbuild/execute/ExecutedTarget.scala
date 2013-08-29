package de.tototec.sbuild.execute

import de.tototec.sbuild.TargetContext

class ExecutedTarget(
    val targetContext: TargetContext,
    val dependencies: Seq[ExecutedTarget]) {
  def target = targetContext.target
  val treeSize: Int = dependencies.foldLeft(1) { (a, b) => a + b.treeSize }
  def linearized: Seq[ExecutedTarget] = dependencies.flatMap { et => et.linearized } ++ Seq(this)
}
