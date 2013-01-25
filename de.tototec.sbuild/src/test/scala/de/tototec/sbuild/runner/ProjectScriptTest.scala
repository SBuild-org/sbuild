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
    assert(cutSimpleComment("""@classpath("hello.jar") // hello""") === """@classpath("hello.jar") """)
    // catch // in strings
    assert(cutSimpleComment("""@classpath("http://example.org/hello.jar") // hello""") === """@classpath("http://example.org/hello.jar") """)
    assert(cutSimpleComment("""@classpath("http://example.org/hello.jar") \/// hello""") === """@classpath("http://example.org/hello.jar") \/""")
    assert(cutSimpleComment("""@classpath("http://example.org/hello.jar") \// hello""") === """@classpath("http://example.org/hello.jar") \// hello""")
  }

}