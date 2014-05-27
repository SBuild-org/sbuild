package org.sbuild

import org.scalatest.FunSuite
import org.sbuild.test.TestSupport
import org.scalatest.FreeSpec
import org.sbuild.internal.BuildFileProject

class TargetRefTest extends FreeSpec {

  val projectA = TestSupport.createMainProject
  val projectB = TestSupport.createMainProject

  val pADir = projectA.projectDirectory

  "TargetRefs toString" - {

    implicit val _ = projectA

    var count = 0
    def testToString(expected: String, targetRefs: TargetRefs) = {
      expected in {
        assert(targetRefs.toString === expected)
      }
    }

    testToString("a ~ b", "a" ~ "b")
    testToString("a ~~ b", "a" ~~ "b")
    testToString("a ~ b ~ c", "a" ~ "b" ~ "c")
    testToString("a ~~ b ~ c", "a" ~~ "b" ~ "c")
    testToString("a ~ b ~~ c", "a" ~ "b" ~~ "c")
    testToString("a ~~ b ~~ c", "a" ~~ "b" ~~ "c")

    testToString(s"file:$pADir/a ~ file:$pADir/b", Path("a") ~ Path("b"))
    testToString(s"file:$pADir/a ~ file:$pADir/b ~ file:$pADir/c", Path("a") ~ Path("b") ~ Path("c"))

  }

  "TargetRef conversions" - {

    "from phony target in same project" in {
      val targetA = {
        implicit val _ = projectA
        Target("phony:a")
      }
      val targetARef: TargetRef = targetA

      assert(targetA.name === targetARef.name)
      assert(projectA.findTarget(targetARef) === Some(targetA))
    }

    "from file target in same project" in {
      val targetA = {
        implicit val _ = projectA
        Target("file:a")
      }
      val targetARef: TargetRef = targetA

      assert(targetA.name === targetARef.name)
      assert(projectA.findTarget(targetARef) === Some(targetA))
    }

    "from phony target in other project" in {
      val projAtargetA = {
        implicit val _ = projectA
        Target("phony:a")
      }
      val projAtargetARef: TargetRef = projAtargetA
      val projBtargetA = {
        implicit val _ = projectB
        Target("phony:a")
      }
      val projBtargetARef: TargetRef = projBtargetA

      assert(projAtargetA !== projBtargetA)
      assert(projBtargetARef.name === projBtargetARef.name)
      assert(projectA.findTarget(projAtargetARef) === Some(projAtargetA))
      assert(projectB.findTarget(projBtargetARef) === Some(projBtargetA))

      assert(projectA.findTarget(projBtargetARef) === None)
      assert(projectB.findTarget(projAtargetARef) === None)
    }

    //     "from phony target in sub project" in {
    //      val projAtargetA = {
    //        implicit val _ = projectA
    //        Target("phony:a")
    //      }
    //      val projAtargetARef: TargetRef = projAtargetA
    //      val projBtargetA = {
    //        implicit val _ = projectB
    //        Module(projectA.projectFile.getAbsolutePath())
    //        Target("phony:a")
    //      }
    //      val projBtargetARef: TargetRef = projBtargetA
    //
    //      assert(projAtargetA !== projBtargetA)
    //      assert(projBtargetARef.name === projBtargetARef.name)
    //      assert(projectA.findTarget(projAtargetARef) === Some(projAtargetA))
    //      assert(projectB.findTarget(projBtargetARef) === Some(projBtargetA))
    //
    //      assert(projectA.findTarget(projBtargetARef) === None)
    //      assert(projectB.findTarget(projAtargetARef) === None)
    // 
    //      assert(projectA.findTarget(projBtargetARef, searchInAllProjects = true) === None)
    //      assert(projectB.findTarget(projAtargetARef, searchInAllProjects = true) === None)
    //    }

  }

}