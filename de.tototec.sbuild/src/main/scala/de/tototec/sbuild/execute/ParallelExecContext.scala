package de.tototec.sbuild.execute

import scala.collection.parallel.ForkJoinTaskSupport
import scala.concurrent.Lock
import scala.concurrent.forkjoin.ForkJoinPool

import de.tototec.sbuild.Logger
import de.tototec.sbuild.Target

/**
 * Context used when processing targets in parallel.
 *
 * @param threadCount If Some(count), the count of threads to be used.
 *   If None, then it defaults to the count of processor cores.
 */
class ParallelExecContext(val threadCount: Option[Int] = None) {
  private[this] val log = Logger[ParallelExecContext]

  private[this] var _locks: Map[Target, Lock] = Map().withDefault { _ => new Lock() }

  /**
   * The used ForkJoinPool, which will use the configured (`threadCount`) amount of threads.
   */
  val pool: ForkJoinPool = threadCount match {
    case None => new ForkJoinPool()
    case Some(threads) => new ForkJoinPool(threads)
  }

  def taskSupport = new ForkJoinTaskSupport(pool)

  /**
   * The first error caught in any of the managed threads.
   */
  private[this] var _firstError: Option[Throwable] = None
  def getFirstError(currentError: Throwable): Throwable = synchronized {
    _firstError match {
      case None =>
        log.debug("Storing FIRST error in thread pool", currentError)
        _firstError = Some(currentError)
        currentError
      case Some(e) =>
        log.debug("Dropping succeeding error in thread pool", e)
        e
    }
  }

  /**
   * The Lock associated with the given target.
   */
  private[this] def getLock(target: Target): Lock = synchronized {
    _locks.get(target) match {
      case Some(lock) => lock
      case None =>
        val lock = new Lock()
        _locks += (target -> lock)
        lock
    }
  }

  def lock(target: Target): Unit = {
    val lock = getLock(target)
    if (!lock.available) log.trace(s"Waiting for target: ${target.formatRelativeToBaseProject}")
    lock.acquire
    log.debug(s"Lock acquired for target: ${target.formatRelativeToBaseProject}")
  }

  def unlock(target: Target): Unit = getLock(target).release

}