package de.tototec.sbuild.execute

import de.tototec.sbuild.Logger
import de.tototec.sbuild.Target

trait TransientTargetCache {
  def cache(target: Target, executedTarget: ExecutedTarget)
  def evict
  def get(target: Target): Option[ExecutedTarget]
}

class InMemoryTransientTargetCache extends TransientTargetCache {
  private[this] var cache: Map[Target, ExecutedTarget] = Map()
  override def cache(target: Target, executedTarget: ExecutedTarget): Unit = synchronized { cache += (target -> executedTarget) }
  override def evict: Unit = synchronized { cache = Map() }
  override def get(target: Target): Option[ExecutedTarget] = cache.get(target)
}

trait LoggingTransientTargetCache extends TransientTargetCache {
  private[this] val log = Logger[LoggingTransientTargetCache]

  abstract override def cache(target: Target, executedTarget: ExecutedTarget): Unit = {
    log.debug("Caching target: " + target)
    super.cache(target, executedTarget)
  }

  abstract override def get(target: Target): Option[ExecutedTarget] = {
    val t = super.get(target)
    t.map { found => log.debug("Found cached target: " + found) }
    t
  }
}