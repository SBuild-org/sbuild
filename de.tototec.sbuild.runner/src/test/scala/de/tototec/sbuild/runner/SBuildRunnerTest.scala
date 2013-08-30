package de.tototec.sbuild.runner

import org.scalatest.FreeSpec
import de.tototec.sbuild.test.TestSupport
import de.tototec.sbuild.Target

class SBuildRunnerTest extends FreeSpec {

  "checkTargets" - {
    "in a project with 1 target should detect 1 cycle: 1 -> 1" in {

      implicit val project = TestSupport.createMainProject
      Target("phony:1") dependsOn "1"

      val checkResult = SBuildRunner.checkTargets(Seq(project))

      assert(checkResult.size === 1)
      assert(checkResult.head._1.name === "phony:1")
    }

    "in a project with 3 targets should detect 1 cycle: 1 -> 1" in {

      implicit val project = TestSupport.createMainProject
      Target("phony:1") dependsOn "1"
      Target("phony:2") dependsOn "3"
      Target("phony:3")

      val checkResult = SBuildRunner.checkTargets(Seq(project))

      assert(checkResult.size === 1)
      assert(checkResult.unzip._1.map { _.name } === Seq("phony:1"))
    }

    "in a project with 4 targets should detect 3 cycle: 2 -> 3 -> 2, 3 -> 2 -> 3, 4 -> 2 -> 3 -> 2" in {

      implicit val project = TestSupport.createMainProject
      Target("phony:1")
      Target("phony:2") dependsOn "3"
      Target("phony:3") dependsOn "2"
      Target("phony:4") dependsOn "2"

      val checkResult = SBuildRunner.checkTargets(Seq(project))

      assert(checkResult.size === 3)
      assert(checkResult.unzip._1.map { _.name } === Seq("phony:2", "phony:3", "phony:4"))
    }

  }

}