package de.tototec.sbuild.runner

import org.scalatest.FunSuite

class AnnotationReaderTest extends FunSuite {

  val annoReader = new AnnotationReader()

  def testCutSimpleComment(source: String, expected: String) =
    test("cutSimpleComment: " + source) {
      assert(annoReader.cutSimpleComment(source) === expected)
    }

  testCutSimpleComment("hello", "hello")
  testCutSimpleComment("// hello", "")
  testCutSimpleComment(" // hello", " ")
  testCutSimpleComment("hello1 // hello2 ", "hello1 ")
  testCutSimpleComment("hello1 /// hello2 ", "hello1 ")
  testCutSimpleComment("hello1 \\// hello2 ", "hello1 \\// hello2 ")
  testCutSimpleComment("hello1 \\/// hello2 ", "hello1 \\/")
  testCutSimpleComment("""@classpath("hello.jar") // hello""", """@classpath("hello.jar") """)
  // catch // in strings
  testCutSimpleComment("""@classpath("http://example.org/hello.jar") // hello""", """@classpath("http://example.org/hello.jar") """)
  testCutSimpleComment("""@classpath("http://example.org/hello.jar") \/// hello""", """@classpath("http://example.org/hello.jar") \/""")
  testCutSimpleComment("""@classpath("http://example.org/hello.jar") \// hello""", """@classpath("http://example.org/hello.jar") \// hello""")
  testCutSimpleComment("""@version("0.3.1") // comment :-)""", """@version("0.3.1") """)

  def testUnescapeStrings(source: String, expected: String) =
    test("unescapeStrings: " + source) {
      assert(annoReader.unescapeStrings(source) === expected)
    }

  testUnescapeStrings("""""", "")
  testUnescapeStrings("""\\""", "\\")
  testUnescapeStrings("""\n""", "\n")
  testUnescapeStrings("""\t""", "\t")

}
