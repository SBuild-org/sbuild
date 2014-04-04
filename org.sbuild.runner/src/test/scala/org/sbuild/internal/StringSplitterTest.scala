package org.sbuild.internal

import org.scalatest.FunSuite

class StringSplitterTest extends FunSuite {

  val splitter = StringSplitter

  val semicolonCases = Seq(
    "par1" -> Seq("par1"),
    "par1;par2" -> Seq("par1", "par2"),
    "par1 ;par2" -> Seq("par1 ", "par2"),
    "par1\\;par2" -> Seq("par1;par2"),
    "par1\\;par2\\;par3" -> Seq("par1;par2;par3"),
    "par1\\;par2\\\\;par3" -> Seq("par1;par2\\", "par3"),
    "par1\\ ;par2" -> Seq("par1 ", "par2"),
    "par1;par2\\" -> Seq("par1", "par2\\")
  )

  semicolonCases foreach {
    case (string, expected) =>
      test(s"semicolon delimited string ${string} should result in ${expected.mkString("'", "','", "'")}") {
        val result = splitter.split(string, ";", Some("\\"))
        assert(result === expected)
      }
  }

  val colonCases = Seq(
    "par1" -> Array("par1"),
    "par1:par2" -> Array("par1", "par2")
  )

  colonCases foreach {
    case (string, expected) =>
      test(s"colon delimited string ${string} should result in ${expected.mkString("'", "','", "'")}") {
        val result = splitter.split(string, ":", Some("\\"))
        assert(result === expected)
      }
  }
}