package de.tototec.sbuild

import org.scalatest.FunSuite
import de.tototec.sbuild.test.TestSupport

class TargetRefTest extends FunSuite {

  implicit val project = TestSupport.createMainProject
  
  val pDir = project.projectDirectory

  private[this] var count = 0
  def testToString(expected: String, targetRefs: TargetRefs) = {
    test(expected) {
      assert(targetRefs.toString === expected)
    }
  }

  testToString("a ~ b", "a" ~ "b")
  testToString("a ~~ b", "a" ~~ "b")
  testToString("a ~ b ~ c", "a" ~ "b" ~ "c")
  testToString("a ~~ b ~ c", "a" ~~ "b" ~ "c")
  testToString("a ~ b ~~ c", "a" ~ "b" ~~ "c")
  testToString("a ~~ b ~~ c", "a" ~~ "b" ~~ "c")

  testToString(s"file:$pDir/a ~ file:$pDir/b", Path("a") ~ Path("b"))
  testToString(s"file:$pDir/a ~ file:$pDir/b ~ file:$pDir/c", Path("a") ~ Path("b") ~ Path("c"))
  
}