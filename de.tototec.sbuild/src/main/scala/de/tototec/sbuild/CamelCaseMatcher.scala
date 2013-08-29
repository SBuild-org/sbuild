package de.tototec.sbuild

import java.util.regex.Pattern

object CamelCaseMatcher {

  def matchCamelCase(fullName: String, shortName: String): Boolean =
    buildPattern(shortName).matcher(fullName).matches()

  def buildPattern(shortName: String): Pattern = {
    val patternString =
      "^" +
        shortName.head.toString +
        shortName.tail.flatMap {
          case char if char.isUpper => "[^A-Z]*" + char.toString
          case char => char.toString
        } +
        "[^A-Z]*$"

    patternString.r.pattern
  }

}

class CamelCaseMatcher(patternString: String) {
  val pattern = CamelCaseMatcher.buildPattern(patternString)
  def matches(string: String): Boolean = pattern.matcher(string).matches()
}