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

  "TargetRef equals contract" - {
    implicit val _ = projectA
    Seq("a", "phony:a") map { ref =>
      s"equals to itself: ${ref}" in {
        val targetRef = TargetRef(ref)
        assert(targetRef == targetRef)
      }
      s"equals to same: ${ref}" in {
        val a = TargetRef(ref)
        val b = TargetRef(ref)
        assert(a == b)
        assert(a.hashCode() == b.hashCode())
      }
    }
  }

  "TargetRefs merge" - {
    implicit val _ = projectA
    def mergeTest(refs: TargetRefs, expected: Seq[Seq[TargetRef]]): Unit = {
      s"merge ${refs} to ${expected}" in {
        assert(refs.targetRefGroups === expected)
      }
    }
    mergeTest("a", Seq(Seq("a")))
    mergeTest("a" ~ "b", Seq(Seq("a", "b")))
    mergeTest("a" ~ "a", Seq(Seq("a")))
    mergeTest("a" ~~ "a", Seq(Seq("a"), Seq("a")))
  }

  "TargetRef conversions" - {

    "from phony target in same project" in {
      implicit val _ = projectA
      val targetA = Target("phony:a")
      val targetARef: TargetRef = targetA

      assert(targetA.name === targetARef.name)
      assert(projectA.findTarget(targetARef) === Some(targetA))
    }

    "from file target in same project" in {
      implicit val _ = projectA
      val targetA = Target("file:a")
      val targetARef: TargetRef = targetA

      assert(targetA.name === targetARef.name)
      assert(projectA.findTarget(targetARef) === Some(targetA))
    }

    "from phony target in other project" in {
      implicit val _ = projectA

      val projAtargetA = Target("phony:a")
      val projAtargetARef: TargetRef = projAtargetA

      val projBtargetA = {
        implicit val _ = projectB
        Target("phony:a")
      }
      val projBtargetARef: TargetRef = projBtargetA

      assert(projAtargetA !== projBtargetA)
      assert(projAtargetARef.name === projBtargetARef.name)
      assert(projAtargetARef.ref !== projBtargetARef.ref)
      assert(projAtargetARef.ref === "phony:a")
      assert(projBtargetARef.ref === s"${projectB.projectFile}::phony:a")

      assert(projectA.findTarget(projAtargetARef) === Some(projAtargetA))
      assert(projectB.findTarget(projBtargetARef) === Some(projBtargetA))

      intercept[TargetNotFoundException] {
        projectA.findTarget(projBtargetARef)
      }
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