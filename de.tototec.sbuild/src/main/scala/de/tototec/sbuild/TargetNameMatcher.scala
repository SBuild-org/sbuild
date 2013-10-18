package de.tototec.sbuild

import java.util.regex.Pattern

object TargetNameMatcher {

  def matchCamelCase(fullName: String, shortName: String): Boolean =
    buildPattern(shortName).matcher(fullName).matches()

  def buildPattern(shortName: String): Pattern = {
    import Pattern.quote

    val patternString = "^" +
      shortName.head.toString +
      shortName.tail.flatMap {
        case char if char.isUpper => s"[^A-Z\\-]*(${quote(char.toString)}|(-${quote(char.toString)})|(-${quote(char.toLower.toString)}))"
        case char => char.toString
      } +
      "[^A-Z\\-]*$"

    patternString.r.pattern
  }

}

class TargetNameMatcher(patternString: String) {
  val pattern = TargetNameMatcher.buildPattern(patternString)
  def matches(string: String): Boolean = pattern.matcher(string).matches()
}

