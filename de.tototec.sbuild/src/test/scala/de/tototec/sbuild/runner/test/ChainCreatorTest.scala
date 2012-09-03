package de.tototec.sbuild.runner.test

import org.scalatest.FunSuite
import de.tototec.sbuild._
import de.tototec.sbuild.runner.SBuildRunner
import java.io.File

class ChainCreatorTest extends FunSuite {

  SBuildRunner.verbose = true
  private implicit val project = new Project(new File("SBuild.scala"), null)

  private val g1 = Target("phony:1")
  private val g2 = Target("phony:2") dependsOn g1
  private val g3 = Target("phony:3")
  private val gA = Target("phony:a")
  private val gB = Target("phony:b")
  private val gC = Target("phony:c")

  test("build chain test 1") {
    assert(Array(g1) === SBuildRunner.preorderedDependencies(List(g1)).map(_.target))
    assert(Array(g1) === SBuildRunner.preorderedDependencies(List(g1), skipExec = true).map(_.target))
  }
  test("build chain test 2") {
    assert(Array(g1, g2) === SBuildRunner.preorderedDependencies(List(g2)).map(_.target))
    assert(Array(g1, g2) === SBuildRunner.preorderedDependencies(List(g2), skipExec = true).map(_.target))
  }
  test("build chain test 3") {
    assert(Array(g1, g1, g2) === SBuildRunner.preorderedDependencies(List(g1, g2)).map(_.target))
    assert(Array(g1, g1, g2) === SBuildRunner.preorderedDependencies(List(g1, g2), skipExec = true).map(_.target))
  }
  test("build chain test 4") {
    assert(Array(g1, g2, g1) === SBuildRunner.preorderedDependencies(List(g2, g1)).map(_.target))
    assert(Array(g1, g2, g1) === SBuildRunner.preorderedDependencies(List(g2, g1), skipExec = true).map(_.target))
  }

}