package de.tototec.sbuild.runner

import de.tototec.sbuild.TargetContext
import de.tototec.sbuild.Target
import de.tototec.sbuild.LogLevel

trait TransientTargetCache {
  def cache(target: Target, executedTarget: ExecutedTarget)
  def evict
  def get(target: Target): Option[ExecutedTarget]
}

class InMemoryTransientTargetCache extends TransientTargetCache {
  private[this] var cache: Map[Target, ExecutedTarget] = Map()
  override def cache(target: Target, executedTarget: ExecutedTarget): Unit = cache += (target -> executedTarget)
  override def evict: Unit = cache = Map()
  override def get(target: Target): Option[ExecutedTarget] = cache.get(target)
}

trait LoggingTransientTargetCache extends TransientTargetCache {
  abstract override def cache(target: Target, executedTarget: ExecutedTarget): Unit = {
    target.project.log.log(LogLevel.Debug, "Caching target: " + target)
    super.cache(target, executedTarget)
  }
  abstract override def get(target: Target): Option[ExecutedTarget] = {
    val t = super.get(target)
    t.map { found => target.project.log.log(LogLevel.Debug, "Found cached target: " + found) }
    t
  }
}