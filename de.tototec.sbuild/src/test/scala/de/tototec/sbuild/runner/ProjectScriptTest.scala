package de.tototec.sbuild.runner

import org.scalatest.FunSuite

class ProjectScriptTest extends FunSuite {

  test("cutSimpleComment") {
    import ProjectScript.cutSimpleComment
    assert(cutSimpleComment("hello") === "hello")
    assert(cutSimpleComment("// hello") === "")
    assert(cutSimpleComment(" // hello") === " ")
    assert(cutSimpleComment("hello1 // hello2 ") === "hello1 ")
    assert(cutSimpleComment("hello1 /// hello2 ") === "hello1 ")
    assert(cutSimpleComment("hello1 \\// hello2 ") === "hello1 \\// hello2 ")
    assert(cutSimpleComment("hello1 \\/// hello2 ") === "hello1 \\/")
  }

}