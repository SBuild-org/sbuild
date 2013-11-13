package de.tototec.sbuild

import org.scalatest.FunSuite

class TargetNameMatcherTest extends FunSuite {

  val testCases = Seq(
    ("aBC", "aaaBbbbCcc", true),
    ("aBbC", "aaaBbbbCcc", true),
    ("aBbCc", "aaaBbbbCcc", true),
    ("abC", "aaaBbbbCcc", false),
    ("aBc", "aaaBbbbCcc", false),
    ("aBBC", "aaaBbBbbCcc", true),
    ("aBbC", "aaaBbBbbCcc", false),

    ("aBC", "aaa-bbbb-ccc", true),
    ("aBC", "aaa-Bbbb-ccc", true),
    ("aBC", "aaa-bbbbCcc", true),
    ("aBC", "aaaBbbb-Ccc", true),
    ("aBC", "aaa-bbb-bCccc", false),
    
    ("a", "aaabbbccc", true),
    ("a", "aaabbbCcc", false),
    ("a", "aaaa-bbbb", false),
    ("aB", "aaaa-Bbbb", true),
    ("aB", "aaaa-BBbb", false),
    ("aB", "aaaaBBbb", false),
    
    ("gG", "greet-goodbye", true)

  )

  val matcher = TargetNameMatcher

  testCases.foreach {
    case (short, long, shouldMatch) =>
      test(s"TargetNameMatcher.matchCamelCase should ${if (shouldMatch) "" else "not "}match ${short} in ${long}") {
        assert(shouldMatch == matcher.matchCamelCase(long, short))
      }

      test(s"TargetNameMatcher.matches should ${if (shouldMatch) "" else "not "}match ${short} in ${long}") {
        assert(shouldMatch == new TargetNameMatcher(short).matches(long))
      }
  }

}
