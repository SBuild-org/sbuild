package org.sbuild.execute

import java.io.File

import org.scalatest.FunSuite

import org.sbuild.NoopCmdlineMonitor
import org.sbuild.Target
import org.sbuild.TargetRef.fromString
import org.sbuild.TargetRefs.fromTarget
import org.sbuild.internal.BuildFileProject

class ChainCreatorTest extends FunSuite {

  private implicit val project = new BuildFileProject(new File("SBuild.scala"), null)

  private val g1 = Target("phony:1")
  private val g2 = Target("phony:2") dependsOn g1
  private val g3 = Target("phony:3")
  private val gA = Target("phony:a")
  private val gB = Target("phony:b")
  private val gC = Target("phony:c")

  val targetExecutor = new TargetExecutor(NoopCmdlineMonitor)
  
  test("build chain test 1") {
    assert(Seq(g1) === targetExecutor.preorderedDependenciesTree(g1).linearized.map(_.target))
    assert(Seq(g1) === targetExecutor.preorderedDependenciesTree(g1, skipExec = true).linearized.map(_.target))
  }
  test("build chain test 2") {
    assert(Seq(g1, g2) === targetExecutor.preorderedDependenciesTree(g2).linearized.map(_.target))
    assert(Seq(g1, g2) === targetExecutor.preorderedDependenciesTree(g2, skipExec = true).linearized.map(_.target))
  }
  test("build chain test 3") {
    assert(Seq(g1, g1, g2) === Seq(g1, g2).flatMap(g => targetExecutor.preorderedDependenciesTree(g).linearized).map(_.target))
    assert(Seq(g1, g1, g2) === Seq(g1, g2).flatMap(g => targetExecutor.preorderedDependenciesTree(g, skipExec = true).linearized).map(_.target))
  }
  test("build chain test 4") {
    assert(Seq(g1, g2, g1) === Seq(g2, g1).flatMap(g => targetExecutor.preorderedDependenciesTree(g).linearized).map(_.target))
    assert(Seq(g1, g2, g1) === Seq(g2, g1).flatMap(g => targetExecutor.preorderedDependenciesTree(g, skipExec = true).linearized).map(_.target))
  }

}