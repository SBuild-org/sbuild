package de.tototec.sbuild

import org.scalatest.FunSuite
import de.tototec.sbuild.test.TestSupport

class TargetRefTest extends FunSuite {

  implicit val project = TestSupport.createMainProject

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

  testToString("file:/tmp/a ~ file:/tmp/b", Path("a") ~ Path("b"))
  testToString("file:/tmp/a ~ file:/tmp/b ~ file:/tmp/c", Path("a") ~ Path("b") ~ Path("c"))
  
}