package de.tototec.sbuild.runner

import org.scalatest.FunSuite

class CamelCaseMatcherTest extends FunSuite {

  val testCases = Seq(
    ("aBC", "aaaBbbbCcc", true),
    ("aBbC", "aaaBbbbCcc", true),
    ("aBbCc", "aaaBbbbCcc", true),
    ("abC", "aaaBbbbCcc", false),
    ("aBc", "aaaBbbbCcc", false),
    ("aBBC", "aaaBbBbbCcc", true),
    ("aBbC", "aaaBbBbbCcc", false)
  )

  val matcher = CamelCaseMatcher

  testCases.foreach {
    case (short, long, shouldMatch) =>
      test(s"CamelCaseMatcher.matchCamelCase should ${if (shouldMatch) "" else "not "}match ${short} in ${long}") {
        assert(shouldMatch == matcher.matchCamelCase(long, short))
      }

      test(s"CamelCaseMatcher.matches should ${if (shouldMatch) "" else "not "}match ${short} in ${long}") {
        assert(shouldMatch == new CamelCaseMatcher(short).matches(long))
      }
  }

}