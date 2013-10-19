package de.tototec.sbuild.runner

import scala.annotation.tailrec
import java.text.ParseException

class AnnotationReader {

  case class SingleVarArgAnnotation(annoName: String, paramName: String, values: Array[String])

  def cutSimpleComment(str: String): String = {
    var index = str.indexOf("//")
    while (index >= 0) {
      // found a comment candidate
      val substring = str.substring(0, index)
      if (substring.length > 0 && substring.endsWith("\\")) {
        // detected escaped comment, ignore this one
        index = str.indexOf("//", index + 1)
      } else {
        // check, that we were not inside of a string
        val DoubleQuote = """[^\\](\\)*"""".r
        val doubleQuoteCount = DoubleQuote.findAllIn(substring).size
        if ((doubleQuoteCount % 2) == 0) {
          // a real comment, remove this one
          return substring
        } else {
          // detected comment in quote
          index = str.indexOf("//", index + 1)
        }
      }
    }
    str
  }

  def unescapeStrings(str: String): String = {

    // http://www.java-blog-buch.de/0304-escape-sequenzen/
    @tailrec
    def unescape(seen: List[Char], str: List[Char]): List[Char] = str match {
      case '\\' :: xs => xs match {
        case '\\' :: ys => unescape('\\' :: seen, ys) // backslash
        case 'b' :: ys => unescape('\b' :: seen, ys) // backspace
        case 'n' :: ys => unescape('\n' :: seen, ys) // newline
        case 'r' :: ys => unescape('\r' :: seen, ys) // carriage return
        case 't' :: ys => unescape('\t' :: seen, ys) // tab
        case 'f' :: ys => unescape('\f' :: seen, ys) // formfeed
        case ''' :: ys => unescape('\'' :: seen, ys) // single quote
        case '"' :: ys => unescape('\"' :: seen, ys) // double quote
        case 'u' :: a :: b :: c :: d :: ys => unescape(Seq(a, b, c, d).toString.toInt.toChar :: seen, ys) // unicode
        case a :: b :: c :: ys if a.isDigit && b.isDigit && c.isDigit && Seq(a, b, c).toString.toInt <= 377 =>
          unescape(Integer.parseInt(Seq(a, b, c).toString, 8).toChar :: seen, ys) // octal with 3 digits
        case a :: b :: ys if a.isDigit && b.isDigit =>
          unescape(Integer.parseInt(Seq(a, b).toString, 8).toChar :: seen, ys)
        case a :: ys if a.isDigit =>
          unescape(Integer.parseInt(a.toString, 8).toChar :: seen, ys)
        case a :: _ => throw new ParseException(s"""Cannot parse escape sequence "\\$a".""", -1) // error
        case Nil => throw new ParseException("""Cannot parse unclosed escape sequence at end of string.""", -1) // error
      }
      case Nil => seen
      case x :: xs => unescape(x :: seen, xs)
    }

    unescape(Nil, str.toList).reverse.mkString
  }

  def findFirstAnnotationWithVarArgValue(buildScript: => Iterator[String], annoName: String, varArgValueName: String): Option[SingleVarArgAnnotation] =
    findFirstAnnotationWithVarArgValue(buildScript, annoName, varArgValueName, false)

  def findFirstAnnotationSingleValue(buildScript: => Iterator[String], annoName: String, valueName: String): String = {
    findFirstAnnotationWithVarArgValue(buildScript, annoName, valueName, singleArg = true) match {
      case None => ""
      case Some(SingleVarArgAnnotation(_, _, Array(value))) => value
      case _ => throw new RuntimeException("Unexpected annotation syntax detected. Expected single arg annotation @" + annoName)
    }
  }

  protected def findFirstAnnotationWithVarArgValue(buildScript: => Iterator[String], annoName: String, varArgValueName: String, singleArg: Boolean = false): Option[SingleVarArgAnnotation] = {
    var inAnno = false
    var skipRest = false
    val it = buildScript
    var annoLine = ""
    while (!skipRest && it.hasNext) {
      var line = cutSimpleComment(it.next).trim

      if (inAnno) {
        if (line.endsWith(")")) {
          skipRest = true
          line = line.substring(0, line.length - 1).trim
        }
        annoLine = annoLine + " " + line
      }
      if (line.startsWith("@" + annoName + "(")) {
        line = line.substring(annoName.length + 2).trim
        if (line.endsWith(")")) {
          line = line.substring(0, line.length - 1).trim
          skipRest = true
        }
        inAnno = true
        annoLine = line
      }
    }

    annoLine = annoLine.trim

    if (annoLine.length > 0) {
      if (annoLine.startsWith(varArgValueName)) {
        annoLine = annoLine.substring(varArgValueName.length).trim
        if (annoLine.startsWith("=")) {
          annoLine = annoLine.substring(1).trim
        } else {
          throw new RuntimeException("Expected a '=' sign but got a '" + annoLine(0) + "'")
        }
      }

      val annoItems =
        if (singleArg) Array(annoLine)
        else
          annoLine.split(",")

      // TODO: also support triple-quotes
      val finalAnnoItems = annoItems map { item => item.trim } map { item =>
        if (item.startsWith("\"") && item.endsWith("\"")) {
          unescapeStrings(item.substring(1, item.length - 1))
        } else {
          throw new RuntimeException("Unexpection token found: " + item)
        }
      }

      Some(SingleVarArgAnnotation(annoName = annoName, paramName = varArgValueName, values = finalAnnoItems))
    } else {
      None
    }

  }
}