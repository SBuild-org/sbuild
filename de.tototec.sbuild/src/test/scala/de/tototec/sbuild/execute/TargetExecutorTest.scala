package de.tototec.sbuild.execute

import org.scalatest.FunSuite

import de.tototec.sbuild.NoopCmdlineMonitor
import de.tototec.sbuild.Target
import de.tototec.sbuild.TargetRef.fromString
import de.tototec.sbuild.TargetRefs
import de.tototec.sbuild.test.TestSupport
import de.tototec.sbuild.toTargetRefs_fromTarget

class TargetExecutorTest extends FunSuite {

  implicit val p = TestSupport.createMainProject
  val deps20 = 1.to(20).map(n => Target("phony:dep" + n))
  val deps30 = 21.to(30).map(n => Target("phony:dep" + n))
  val compile = Target("phony:compile") dependsOn deps20.foldLeft(TargetRefs())(_ ~ _)
  val test = Target("phony:test") dependsOn compile ~ deps30.foldLeft(TargetRefs())(_ ~ _)
  val all = Target("phony:all") dependsOn compile ~ test

  val expectedAll =
    // all
    1 +
      // compile
      1 + 20 +
      // test
      1 + 1 + 20 + 10

  test("Exec tree node count") {
    val targetExecutor = new TargetExecutor(monitor = NoopCmdlineMonitor)
    assert(new TargetExecutor(monitor = NoopCmdlineMonitor).calcTotalExecTreeNodeCount(Seq()) === 0)
    assert(new TargetExecutor(monitor = NoopCmdlineMonitor).calcTotalExecTreeNodeCount(Seq(all)) === expectedAll)
    assert(new TargetExecutor(monitor = NoopCmdlineMonitor).calcTotalExecTreeNodeCount(Seq(all, all)) === (2 * expectedAll))
  }

  test("Exec tree node count with shared DependencyCache") {
    val targetExecutor = new TargetExecutor(monitor = NoopCmdlineMonitor)
    val dependencyCache = new TargetExecutor.DependencyCache()
    assert(new TargetExecutor(monitor = NoopCmdlineMonitor).calcTotalExecTreeNodeCount(Seq(), dependencyCache = dependencyCache) === 0)
    assert(new TargetExecutor(monitor = NoopCmdlineMonitor).calcTotalExecTreeNodeCount(Seq(all), dependencyCache = dependencyCache) === expectedAll)
    assert(new TargetExecutor(monitor = NoopCmdlineMonitor).calcTotalExecTreeNodeCount(Seq(all, all), dependencyCache = dependencyCache) === (2 * expectedAll))
  }

}